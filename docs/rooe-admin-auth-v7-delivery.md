# 认证与安全 v7 实施交付

实施依据：`rooe-admin-authentication-authorization-implementation-plan-v7.md`。变更保留现有 Maven 模块和 Request / DTO / Assembler / Service / Mapper / DO 分层。

## 模块职责

| 层                                | 实现                                                                                                                     |
|----------------------------------|------------------------------------------------------------------------------------------------------------------------|
| admin-common                     | 复用统一 ApiResult 和错误码，补充认证错误码                                                                                            |
| admin-framework / framework      | Spring Security 配置、TokenAuthenticationFilter、TokenService、SessionService 与身份读取接口、PermissionChecker、PasswordPolicy、审计指标 |
| admin-framework / infrastructure | 复用 StringRedisTemplate / Redisson，承载会话、认证缓存和单 Key Lua                                                                  |
| admin-system / modules.security  | 登录快照、认证业务、权威读事务、用户与角色缓存、AdminPrivilegeGuard、认证版本递增、事务提交后失效                                                             |
| admin-system / modules.system    | 在现有用户、角色、菜单写事务接入守卫和缓存失效；个人资料、头像只更新对应字段                                                                                 |
| admin-bootstrap                  | 配置、增量 V4 迁移、组装及集成测试                                                                                                    |
| rooe-admin-web                   | localStorage 双 Token、401 静默续期与请求重放、强制改密、跨标签登出、空闲退出、CSP                                                                 |

旧 JWT、验证码登录、在线会话与踢人接口已移除。旧的 JWT Token 不能用于新认证链路。数据范围逻辑仍归原有 DataService 管理。

## API 与兼容变化

认证统一使用 `Authorization: Bearer <token>`，不使用 Cookie 登录凭证。Token 为 32 字节安全随机数的 43 字符 Base64URL；Redis 只存 SHA-256 摘要索引。

| 方法与路径                   | 请求 / 行为                                                                                                                                             |
|-------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| POST /api/auth/login    | username、password、可选 clientType；返回 status=SUCCESS、accessToken、refreshToken、tokenType、expiresIn、absoluteExpiresAt、mustChangePassword、上次登录时间与 IP；禁止缓存 |
| GET /api/auth/me        | 用户资料、有效权限码集合 roles、mustChangePassword；不返回密码和内部角色码                                                                                                   |
| POST /api/auth/logout   | 按会话吊销 Access 与 Refresh 两把凭证，204                                                                                                                     |
| POST /api/auth/refresh  | refreshToken；轮换并返回新的 accessToken 与 refreshToken，旧凭证立即失效；凭证重放吊销整条会话；禁止缓存                                                                             |
| POST /api/auth/password | oldPassword、newPassword；旧密码错误400，成功204，认证版本递增，重新登录                                                                                                  |

原 `/auth/*` 登录和 `/api/users/updatePass` 调用方需要切换。401 返回 WWW-Authenticate: Bearer；普通权限拒绝403；认证基础设施不可用503。登录和修改密码已取消次数限流。前端仅在401时清理登录，403/503保留；强制改密403跳转专用页面。

强制改密用户仅可访问 me、password、logout、refresh，并且没有业务 authorities。超管基于启用的 SUPER_ADMIN 角色判断，不使用
isAdmin 字段放行。

## 配置、迁移与初始化

- V1–V3 保持不变；V4 增加角色、菜单 enabled，补充已有控制器缺少的权限种子，停用旧在线会话菜单。
- 菜单、用户角色及角色详情的业务缓存使用 v7 命名空间，避免升级时复用缺少 enabled 字段的旧值；认证缓存使用独立 rooe:auth 前缀。
- V4 仅禁用仍使用已知演示密码哈希的 admin/test；已自定义密码的账户保持原状态。
- `AUTH_INITIAL_PASSWORD` 必须至少8字符、不等于用户名、UTF-8不超过72字节。配置后一次性初始化历史演示 admin，并要求改密；没有配置时不恢复已禁用演示账户。新建用户和管理员重置密码也读取该配置。
- `AUTH_ALLOWED_ORIGINS` 配置实际前端来源，用逗号分隔，禁止通配符。
- 默认 Access 15 分钟、Refresh 30 天（即会话绝对生命周期）；用户缓存60秒、角色权限缓存5分钟，均有±10%抖动；auth
  配置不再包含登录或改密限流参数。
- 默认前端空闲20分钟、提前2分钟提示。Access 过期由 401 触发一次性静默续期并重放原请求；计时器、页面重新可见和网络恢复只检查过期，不产生网络请求。
- 服务端不再按请求续期，因此没有 `renew-exclude-paths`；续期只发生在 `/api/auth/refresh`，且必须轮换 Refresh。前端并发 401
  通过单飞 Promise 合并为一次续期。
- 登录密码通过 HTTPS 传输，不再依赖前端 RSA 登录包装。已有邮箱验证相关逻辑保留。

部署前需要前后端一起升级，并运行 Flyway 迁移。初始化密码不得写进仓库或日志。

## 一致性与故障边界

禁用、删除、本人改密、管理员重置/强制改密与 auth_version 在同一数据库事务提交或回滚。普通用户重新启用和角色关系变化不递增版本；权限靠缓存失效和 TTL 刷新。

缓存失效统一要求活动事务及事务同步，提交后删除，失败重试3次并记指标。该进程内事件没有可靠投递保证；进程崩溃或失效失败时，用户旧缓存窗口最多约66秒，角色旧权限窗口最多约5.5分钟。DB
回源通过独立 READ_COMMITTED 短事务读取主库；回填截止时间在查询前确定，回填用 `setIfAbsent`（`SET NX PX`）防止覆盖并发新值，刷新用
compare-and-refresh 脚本防止覆盖并发新值或延长旧快照窗口。

普通退出依赖 Redis DEL；Redis 故障转移丢失该删除时，旧会话可能复活。用户级撤销依赖持久化 auth_version，不能与普通退出的保证混淆。

R1–R6 检查在既有用户/角色/菜单 Service 的写事务执行，仅使用数据库权限；先锁 SUPER_ADMIN 角色公共行，再检查并写入，串行保护最后一个启用超管。没有新增项目原先不存在的管理接口。

## 验证记录

真实 Redis 测试通过 `AUTH_TEST_REDIS_URL` 显式启用，未设置时跳过；本次使用临时本地 Redis8.10.2，不连接或修改生产 Redis。事务测试使用独立 H2 MySQL 模式，不能替代实际 MySQL 的部署验证。

认证请求预算已经通过真实 Redis 集成测试：预热脚本和用户/角色缓存后，完整 TokenAuthenticationFilter 请求执行一次 EVALSHA、一次 GET、一次 MGET，合计3次 Redis 操作；权威数据库读取服务调用为0。该统计针对认证部分，不包含业务控制器自己的数据查询；Redis 脚本缓存冷启动的 NOSCRIPT 重试不计入预热路径。

前端3个测试套件、25项用例通过，改动文件 lint 与生产构建通过。输出 CSP 使用外部 runtime 脚本。日志测试实际检查新旧密码、Authorization、Cookie、accessToken 被替换为 XXX；P6Spy 只输出 SQL 模板并隐藏密码相关语句，Druid 关闭慢 SQL 绑定参数输出。

后端最终汇总：151项用例，148项通过、3项因外部 MySQL/Redis 不可达跳过，当前源码对应的 Surefire 报告无失败或错误。先运行全量回归；唯一失败是旧错误码格式约束与 v7 冲突，之后仅修改该测试并定向复测，其他已通过用例没有重复执行。原错误码唯一性检查兼容 v7 的 `AUTH_枚举名` 语义码，其他模块仍要求数字编号；保留所有已有错误码。

| 验证 | 结果 |
| --- | --- |
| 真实 Redis 会话 / 限流 / 原子脚本 | 7项通过，包括退出与续期竞态、绝对/空闲过期、并发限额、固定窗口恢复和过期截止时间 |
| 安全过滤链与方法授权 HTTP | 7项通过，包括401/403/503、强制改密、版本失效、CORS和续期排除 |
| 登录凭证与快照 | 5项通过，包括不存在/错误密码一致、禁用状态在密码校验后检查、同次快照版本、密码擦除及限流顺序 |
| 真实 Redis 用户/角色缓存 | 3项通过，包括3次访问/0数据库查询、版本落后修复、并发回源合并、缓存故障回源 |
| 真实 MyBatis / 事务 / AFTER_COMMIT | 13项通过，包括重置/改密/禁用/删除、版本与业务回滚、失效重试、角色/菜单失效、R1–R6和最后超管并发保护；既有用户/角色 HTTP 入口亦覆盖越权403 |
| 所有生产 Controller 授权及种子码 | 通过；未知权限码0，无遗漏授权声明 |
| HTTP与SQL日志脱敏 | 通过；包含真实日志内容断言 |
| 原有统一响应、数据范围、转换器等回归 | 通过 |
| ErrorCodeRegistryTest 定向复测及 verify 打包 | BUILD SUCCESS |

现有用户、角色、菜单关键写入口均接入守卫，没有遗漏的既有管理写入口。成品 JAR 检查未发现旧 JWT/验证码运行依赖或已删除认证类，包含 V4 迁移。

```powershell
$env:JAVA_HOME = 'D:\Program Files\Java\jdk-21'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:AUTH_TEST_REDIS_URL = 'redis://127.0.0.1:16379'
mvn -pl rooe-admin/admin-bootstrap -am test
# 本次实际先运行全量 verify，再对修正后的错误码契约测试定向复测并完成打包：
mvn -pl rooe-admin/admin-bootstrap -am verify
mvn -pl rooe-admin/admin-bootstrap -am verify "-Dtest=ErrorCodeRegistryTest" "-Dsurefire.failIfNoSpecifiedTests=false"
# 在 rooe-admin-web 下
node node_modules/@vue/cli-service/bin/vue-cli-service.js test:unit --runInBand
npm run build:prod
```

## 后续代码优化（2026-10-19）

本轮沿用现有 framework、infrastructure、modules 分层及 v7 安全约束，改进以下位置：

- framework：复用路径匹配器并预编译令牌格式表达式，减少认证请求中的重复分配；过滤器将认证准备与下游业务调用分开，统一在 finally 清理上下文，覆盖匿名请求、拒绝响应写出失败及下游异常。
- 前端认证工具：统一校验请求携带的 Bearer 是否属于当前会话；旧会话的401/强制改密403不能清除或修改新会话，旧响应也不能刷新新会话的续期时间。
- 会话交互：绝对过期窗口内只提示一次；仅在空闲截止时间实际延长后重新允许提示；续期失败与退出回调异常均恢复状态标记，避免无限重试。
- Vuex：使用 async/await 简化用户信息和退出操作，返回时校验会话归属，避免旧用户信息覆盖新登录或旧退出清除新登录。
- 路由：等待菜单路由和侧栏生成完成，统一捕获失败并恢复重试状态；会话切换后丢弃旧菜单，不再安装旧动态路由。

本轮前端4个套件、43项测试通过，改动文件 lint 及生产构建通过。新增用例覆盖旧会话响应竞态、绝对过期提示、异常后重试，以及菜单生成失败和切换会话时的导航处理。

后端本轮定向回归38项全部通过、无跳过，Maven 输出 BUILD SUCCESS：安全过滤链9项、真实 Redis 会话与限流7项、登录5项、真实 Redis 缓存3项、事务与 AFTER_COMMIT 13项、控制器授权覆盖1项。其中新增2项过滤器测试覆盖匿名下游异常和拒绝响应写出失败后的上下文清理；预热后完整认证仍为3次 Redis 操作、0次数据库读取。本轮使用独立临时 Redis 实例，前文全量回归记录属于初次实现的基线验证。

```powershell
# 使用上方的 JDK21 与 AUTH_TEST_REDIS_URL 环境设置，在项目根目录执行：
mvn -pl rooe-admin/admin-bootstrap -am test "-Dtest=SecurityHttpIntegrationTest,AuthLoginTest,AuthCacheIntegrationTest,AuthMutationIntegrationTest,ControllerAuthorizationCoverageTest,RedisSecurityIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

## 保持功能的代码精简

- `request.js` 与 `upload.js` 共用 `handleApiResponse`，集中处理业务包装、续期记录及业务失败；普通请求继续返回业务数据，上传继续返回包含 data、status、headers、config 的完整 Axios 响应。
- `SecurityPaths.isPublic` 复用已有路径匹配方法，公开路径及匹配规则不变。
- `RoleServiceImpl` 移除未使用的 `UserCacheManager` 注入，菜单 ID 集合只计算一次；公共锁、权限守卫、事务及提交后失效逻辑保持原顺序。
- Vuex 默认角色赋值合并为一个条件表达式，保留无权限时的默认角色。

本轮前端4个套件、44项测试通过，改动文件 lint、生产构建通过。上传用例同时检查统一包装和旧格式响应，确认业务数据、状态码、响应头及 config 保持一致。

后端定向回归29项全部通过，无失败、错误或跳过，Maven 输出 BUILD SUCCESS：安全过滤链9项、角色数据范围3项、真实 Redis 缓存3项、事务与 AFTER_COMMIT 13项、控制器授权覆盖1项。预热认证仍为3次 Redis 操作、0次数据库读取。

```powershell
# 沿用前述 JDK21 与 AUTH_TEST_REDIS_URL 设置：
mvn -pl rooe-admin/admin-bootstrap -am test "-Dtest=SecurityHttpIntegrationTest,AuthCacheIntegrationTest,AuthMutationIntegrationTest,ControllerAuthorizationCoverageTest,RoleServiceImplDataScopeTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

## 取消登录及改密限流（2026-10-19）

- 删除账号/IP 登录尝试配额、登录成功配额和修改密码尝试配额。
- 删除 `LoginRateLimiter`、`fixed-window.lua`、`auth.login-limit` 与 `auth.password-limit`，并清理认证限流错误码、Retry-After 响应处理及对应 CORS 暴露头。
- 密码验证、禁用状态检查、改密用户行锁、认证版本递增、事务提交后缓存失效与 Redis 会话机制保持原逻辑。
- 登录测试改为覆盖超过原尝试配额后继续验证、超过原成功配额后继续签发，以及会话存储故障返回503并擦除凭证。改密事务测试覆盖超过原配额的错误密码尝试后仍可正确改密；删除已失效的限流脚本测试。
- 前文测试数量为各次历史验证记录。取消限流时的首次回归曾被工作区已有的条件语句语法错误阻断；后续修复结果见下节。

## 编译修复与回归（2026-10-19）

- 修复19个文件中损坏的条件表达式、方法调用、括号和循环，保留现有分层与认证流程。
- 恢复 Token 格式检查、用户启用状态与认证版本判断、缓存截止时间检查，以及角色授权和超级管理员保护条件。
- 恢复菜单树组装、菜单父级处理、递归收集去重、用户查询与头像清理、字典空列表处理，并修复测试中的数据初始化和并发请求循环。
- 登录与修改密码限流继续保持删除状态。
- 276个 Java 源文件语法检查通过；定向回归30项全部通过，无失败、错误或跳过：安全过滤链9项、登录7项、事务与 AFTER_COMMIT 13项、控制器授权覆盖1项。
- 使用 JDK21 与独立 Redis 实例完成全量 `clean verify`，BUILD SUCCESS；共153项测试，其中150项通过、3项因未启用外部集成环境而跳过，无失败或错误。

## 认证响应统一包装（2026-10-19）

- `AuthController.login` 和 `me` 直接返回业务 DTO，成功响应统一由 framework 层的 `ApiResultResponseAdvice` 包装。
- 将 `CurrentUser` 从 Controller 内部类移至 `modules.security.domain.dto`，保留用户资料、权限码集合及强制改密标记。
- Advice 原来跳过所有带 `ServletResponse` 参数的方法；现仅跳过返回 `void` 且带该参数的直接响应方法，保留 DTO 接口设置缓存响应头的能力。
- `@ResponseStatus(NO_CONTENT)` 接口跳过包装，注销、续期和改密保持204空响应；已经是 `ApiResult` 的结果仍不会二次包装。
- 调整后27项定向回归全部通过，无失败、错误或跳过，并完成 `verify` 打包：统一响应13项、实际认证入口响应4项、HTTP日志6项、参数校验3项、Controller授权覆盖1项。

## 精简重复认证注解（2026-10-19）

- 删除7个 Controller 中15处仅检查登录状态的 `@PreAuthorize("isAuthenticated()")`，继续由全局过滤链的 `.anyRequest().authenticated()` 统一认证。
- 业务权限表达式 `@perm.has(...)` 和方法级安全配置继续保留。
- 授权覆盖测试按请求方法和路径明确列出15个只需登录的接口，继续检查其他非公开接口的权限声明及种子权限码。
- 安全过滤链测试使用无权限注解的接口，验证未携带 Token、Token 格式错误时均返回401，已认证且无业务权限的用户可访问仅需登录的接口。
- 15项定向回归全部通过，无失败、错误或跳过：安全过滤链10项、实际认证入口响应4项、Controller授权覆盖1项，Maven 输出 BUILD SUCCESS。

## 清理匿名访问注解（2026-10-19）

- 删除 `annotation.rest` 下6个匿名访问注解，以及不再使用的扫描工具 `AnonTagUtils` 和专用枚举 `RequestMethodEnum`。
- 首页改为普通 `@GetMapping("/")`；公开路径继续由 `SecurityPaths.PUBLIC_PATHS` 统一配置，匿名访问范围保持一致。
- `clean verify` 清理构建通过，安全过滤链10项、Controller授权覆盖1项全部通过，无失败、错误或跳过；成品 JAR
  已确认不含删除的注解、扫描工具和枚举。

## 运维核验状态

| 项目                                   | 状态                                                                      |
|--------------------------------------|-------------------------------------------------------------------------|
| Redis 6.2+、单分片非 Cluster              | 会话脚本与 cache-refresh 脚本依赖 PXAT，临时本地 Redis8 单实例已验证；实际部署未验证                |
| Redis 持久化、复制、noeviction、ACL、TLS与故障转移 | 未验证，未更改部署                                                               |
| HTTPS、可信代理与来源配置                      | 代码使用 remoteAddr；实际反向代理信任边界未验证                                           |
| 应用与 Redis NTP 时间同步                   | 未验证；会话脚本与 cache-refresh 脚本的 PXAT 依赖节点时间同步（缓存回填已改用 `setIfAbsent`，不再有该依赖） |
| MySQL 迁移与 InnoDB 并发锁                 | 隔离事务测试覆盖；实际数据库连通与迁移未验证                                                  |
| 日志脱敏                                 | 配置覆盖 Authorization、Cookie及新旧密码、accessToken 等 JSON 字段；自动化输出检查见测试结果       |
| 浏览器 CSP                              | 生产构建输出无内联启动脚本；实际部署来源与业务页面浏览器验收未验证                                       |

不包含 MFA、数据权限新设计、顶号、会话列表、按用户会话索引或踢人功能。
