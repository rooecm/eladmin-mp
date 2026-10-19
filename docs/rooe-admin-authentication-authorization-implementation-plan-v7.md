# Rooe-Admin 认证与鉴权实施方案（v7）

> 供 AI Agent 按本文实施。本文仅覆盖身份认证、会话管理、功能权限鉴权，不包含数据权限。
>
> 技术基线：JDK 17、Spring Boot 3、Spring Security 6.x、Redis（项目已集成，复用项目现有 Redis 客户端；若同时存在
`StringRedisTemplate` 与 Redisson，优先 `StringRedisTemplate`）。

## 0. v7 修订说明

v7 基于 v6 修订，保留不透明 Token、Redis 服务端会话、Spring Security + RBAC 和用户级 `auth_version`
。本版优先保证每请求成本稳定，不在普通请求中增加数据库查询。

| 领域      | v7 修订                                                                                                                                                              |
|---------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 用户缓存一致性 | 缓存未命中时，在数据库读取开始前确定缓存绝对截止时间；写入使用 Redis `SET NX PXAT`，不覆盖并发写入的新缓存。旧数据即使在缓存删除后被并发回填，也不能把有效期延长到读取开始时间加 TTL 之后                                                          |
| 用户状态校验  | 正常请求继续使用 Redis 用户缓存，不逐请求查数据库。若 `session.authVersion > cachedUser.authVersion`，视为缓存可能落后，绕过缓存回源主库并修复缓存；若会话版本小于可信用户版本，则拒绝请求                                           |
| 撤销保证    | 数据库事务内递增 `auth_version`；缓存失效是加速手段。用户禁用、删除、改密、重置密码的旧会话，在读取到新版本后立即拒绝；缓存/事件异常时，基于 60s ±10% 的绝对缓存截止时间，最多约 66s 后回源校验（另受应用与 Redis 时钟偏差影响，因此必须 NTP 同步）。已在变更提交前开始的请求可能继续完成 |
| 登录限流    | 账号/IP 登录尝试限流改为预认证原子准入，计数所有尝试，不再采用“先检查失败数、密码校验失败后才递增”的竞态模式；成功建会话的额度检查也必须原子化                                                                                         |
| 响应头     | `X-Session-Expires-In` 表示认证 Filter 已刷新会话 TTL；由于它发生在 Controller 授权/处理之前，403 或后续 5xx 也可能带该头。前端只在 2xx 且存在该头时更新有效保活时间                                                  |
| 角色权限缓存  | 默认 TTL 从 30m 缩短为 5m（±10%），缓存失效失败时旧权限窗口上限约 5.5m；不增加普通请求的 Redis 往返次数                                                                                                 |
| 故障边界    | `auth_version` 保护用户级撤销；普通登出依赖会话 Key 的 Redis `DEL`，主从切换若丢失该写入，登出会话可能在剩余 TTL 内复活。文档不再把两种撤销混为一谈                                                                       |
| 安全校验    | 本期不提供每请求查库的 每请求查库模式，避免正常请求产生额外数据库压力。所有认证关键缓存回源强制使用主库/权威读，不使用延迟副本或 ORM 查询缓存                                                                                         |
| 其他修正    | 修复超级管理员验收与保护规则矛盾；补充 BCrypt 密码 UTF-8 字节长度校验；区分安全审计、普通日志和指标中的个人信息                                                                                                    | 

> 性能目标：普通的已认证请求不查询数据库，约 3 次 Redis 往返：单 Key 会话 Lua、用户缓存 `GET`、角色权限缓存 `MGET`
> 。缓存未命中、缓存版本落后、登录、管理越权校验等特殊路径才访问数据库。撤销承诺是有界最终生效，不是跨 DB/Redis 的强一致即时撤销。

## 1. 目标与范围

### 1.1 本期必须实现

- 前后端分离，支持浏览器、App、小程序通过统一认证接口登录。
- 使用随机不透明 Token，不使用 JWT；Token 通过 `Authorization: Bearer` 传递。
- Redis 服务端会话、空闲滑动过期、绝对最大存活时间、登出撤销。
- 基于 Spring Security 6 的请求认证、RBAC 功能权限鉴权、401/403/503 统一处理。
- 登录、退出、获取当前用户、用户操作触发的 `/api/auth/touch`、修改本人密码。
- 用户状态校验、强制改密限制；用户禁用/删除/改密/重置密码，或强制设置 `must_change_password=true` 时，在同一事务内递增
  `auth_version`；提交后失效用户缓存。
- 用户基础信息缓存、角色权限缓存；角色/权限变更后失效相关缓存。
- 登录尝试准入限流与成功建会话频率限制、必要的认证安全审计；复用项目已有审计机制和指标体系。
- 管理操作越权防护（8.6）：若项目已有对应用户/角色管理接口，则接入现有 Service；不存在的业务接口不为本方案额外创建。
- 对会话创建、认证续期实现 Redis 单 Key 原子操作及并发测试（在真实 Redis 上运行）；会话撤销依赖 `auth_version`，不依赖用户会话索引。

### 1.2 复用已有能力

Agent 必须先检查项目并优先复用，不得重复建设：

- 统一响应体、业务错误码和全局异常处理。
- 数据库用户/角色/权限实体、Repository/Mapper、迁移机制和初始数据机制。
- Redis 客户端与序列化配置；若项目同时使用 `StringRedisTemplate` 与 Redisson，优先使用 `StringRedisTemplate` 执行简单
  Redis 命令和 Lua 脚本。
- `PasswordEncoder`、可信代理/IP 解析、CORS 配置。
- 审计日志、Micrometer/指标平台、日志脱敏和告警设施。
- 已存在的用户禁用、删除、密码重置、角色权限维护接口及其业务校验。

若项目缺少实现本方案必要的数据字段或认证/鉴权能力，可以补齐；不要顺带重构无关模块。

### 1.3 仅运维检查，不要求 Agent 自动修改环境

- Redis `maxmemory-policy noeviction`、容量与告警、AOF、生产高可用（哨兵或主备版，**非 Cluster**）、ACL、网络隔离/TLS。
- 网关、访问日志、APM 对 `Authorization` 请求头的脱敏。
- HTTPS、时间同步、生产环境密钥/初始密码配置。

Agent 无权或不适合直接修改部署环境时，应输出检查清单和“已验证/未验证/不符合”状态，不得声称已完成环境配置。

### 1.4 后续预留 / 本期不实现

- JWT、OAuth 2.0、Refresh Token。
- 数据权限（部门、本人、自定义数据范围等）。
- MFA（TOTP 等）和敏感操作二次验证（step-up）。登录结果 `status`、`AuthService` 两阶段结构仅保持轻量扩展点，不实现挑战流程。
- 多端登录策略配置、顶号、踢人、会话列表、设备会话管理。本期不建立用户会话索引；会话 Hash 已写入 `clientType`、`loginIp`、
  `userAgent` 供将来使用（5.2）。
- 验证码（本期以登录限流为主，后续按需追加）。
- 若项目不存在，管理员重置他人密码接口本身不新建；若项目已有则接入 `bumpAuthVersion`、强制改密与用户缓存失效。
- 权限版本号、事务 Outbox、跨 DB/Redis 的强一致撤销框架。本期只引入用户级 `auth_version` 这一个单调递增整数用于会话撤销，不做权限版本号。
- 前端菜单展示规则以外的前端安全控制。前端隐藏按钮不能替代后端授权。

## 2. 关键决策（Agent 不得自行更改；与项目现状冲突时停止并在汇总中报告）

| #   | 决策点          | 结论                                                                                                | 说明                                                                                               |
|-----|--------------|---------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| D1  | Token        | 256-bit 随机不透明串，`Authorization: Bearer` 传输                                                         | 不使用 JWT                                                                                          |
| D2  | 会话存储         | 单 Hash，Key 为 `rooe:auth:session:{tokenDigest}`，记录登录时的 `authVersion`；登出时删除 Key                     | 无 `status` 字段；没有用户会话索引，将来做会话列表/踢人时再增加管理用索引，它不承担撤销的安全职责                                           |
| D3  | 续期           | 所有符合续期条件的请求均由 `SessionService` 使用 Lua 原子校验会话并刷新 TTL；新 TTL = min(空闲超时, 距绝对过期剩余)                    | 本期不设置续期节流（将来需要时只能在 Lua 脚本内按 TTL 节流），续期与撤销不会互相复活会话                                                |
| D4  | 空闲保活         | 前端在「有真实用户操作且距最近一次服务端确认续期成功（响应头 `X-Session-Expires-In`）≥ touch-interval」时调用 `POST /api/auth/touch` | 是否续期由服务端响应头告知，前端不维护可续期清单；不做定时心跳                                                                  |
| D5  | 浏览器 Token 存储 | `localStorage`，配合 12.2 第 7 条的前提条件                                                                 | 正常登出会删除服务端会话 Key；Redis 故障切换丢失 `DEL` 时可能复活，见 5.5。若威胁模型要求更高，另行评估 HttpOnly + SameSite Cookie 与 CSRF |
| D6  | CSRF         | 关闭                                                                                                | 凭据仅通过 `Authorization` 头传递，不存在 Cookie 会话认证；在配置处写注释说明                                              |
| D7  | 登录认证         | `AuthenticationManager` + `DaoAuthenticationProvider` + `PasswordEncoder`                         | 用户状态检查推迟到密码验证成功之后                                                                                |
| D8  | 授权表达式        | `@PreAuthorize("@perm.has('sys:user:list')")`，注解放在 Controller 方法上                                 | 统一入口，便于以后扩展                                                                                      |
| D9  | 超级管理员        | 角色码由 `auth.super-admin-role-code` 配置（默认 `SUPER_ADMIN`）；拥有该启用角色的用户 `perm.has(...)` 恒为 true         | 不信任任何客户端传来的管理员标志                                                                                 |
| D10 | 默认拒绝         | 由自动化测试保证：每个 Controller 接口必须带 `@PreAuthorize` 或属于公开端点清单                                            | 注解式授权本身是 opt-in，需要测试兜底                                                                           |
| D11 | Redis 故障     | 会话相关 Redis 异常 → 503，不放行；权限缓存异常 → 回源数据库；数据库也失败 → 503                                               | 503 不应触发前端清除登录状态                                                                                 |
| D12 | 缓存 TTL       | 用户缓存默认 60s、角色权限缓存默认 5m，均 ±10% 随机抖动；缓存回填使用绝对截止时间                                                   | 用户缓存承载 `status` 与 `authVersion` 的安全判断；角色权限缓存 TTL 是旧权限窗口上限                                        |
| D13 | 角色/权限变更      | 只失效缓存，不撤销会话                                                                                       | 权限在每次请求时从缓存加载                                                                                    |
| D14 | 会话撤销         | 登出删除当前会话 Key；禁用、删除、改密、重置密码在同一数据库事务内递增 `auth_version`。用户级撤销在请求读到新版本时生效，缓存窗口最多约 66s                 | 普通登出与用户级撤销的故障保证不同，见 5.5                                                                          |
| D15 | 管理操作越权防护     | 分配的角色/权限必须在操作者自身权限范围内；不可自改角色；超管角色与账号受保护；至少保留一个启用的超管；校验在 Service 层读库完成                             | 见 8.6                                                                                            |
| D16 | Redis 部署     | 认证数据所在实例 `noeviction`，AOF everysec，生产高可用；Redis 写失败按 503 处理                                        | 见 5.7                                                                                            |
| D17 | 强制改密         | 用户有 `must_change_password` 标记；标记为 true 时会话仅能访问改密相关接口                                              | 见 6.5                                                                                            |
| D18 | MFA          | 本期不实现，仅预留登录结果 `status` 与 `AuthService` 的两阶段结构                                                     | 见 6.1                                                                                            |
| D19 | 可观测与响应头      | Micrometer 指标；登录响应 `Cache-Control: no-store`                                                      | 见 6.4                                                                                            |
| D20 | Redis 部署形态   | 使用 Redis Sentinel 或云托管主备版，当前阶段不使用 Cluster                                                         | 当前脚本只访问单个 Key；非 Cluster 是基于管理后台规模与运维简单度的选择，不是 Lua 与 Cluster 天然冲突；角色缓存使用 `MGET`                   |
| D21 | 认证版本号        | 用户表 `auth_version`（单调递增整数）；会话记录登录时的版本；Filter 发现会话版本小于用户版本即拒绝并删除会话                                 | 递增时机：禁用、删除、改密、重置密码、强制设置 `must_change_password=true`、（将来）强制下线；启用用户与角色权限变更不递增；见 5.5                |
| D22 | 时间来源         | Lua 脚本的 `now` 由应用 `Clock` 经 `ARGV` 传入；`createdAt`、`absoluteExpireAt` 均由应用 `Clock` 生成              | 不混用数据库或 Redis 的时钟；`auth_version` 不依赖时钟；见 5.4、5.6                                                 |
| D23 | 有效保活判定       | 前端只在 2xx 且存在 `X-Session-Expires-In` 时更新时间                                                         | 该头可因 Filter 先行续期而出现在后续 403/5xx 响应中；前端不得把这些响应算作有效保活                                               |
| D24 | 会话增长控制       | 每账号成功建会话的额度检查为原子操作（默认 10m 内最多 30 次），超过返回 429                                                      | 无用户会话索引，本期以成功建会话频率约束会话增长；见 6.2                                                                   |

## 3. 总体架构

请求处理链路：

1. 客户端调用登录接口，提交账号和密码。
2. 服务端先原子预占账号/IP 登录尝试额度，再校验凭据，最后复核用户状态。
3. 使用安全随机数生成不透明 Token。
4. 服务端以 Token 摘要为 Key 创建会话，会话记录登录时用户的 `auth_version`。
5. 客户端后续请求通过 `Authorization: Bearer <token>` 携带 Token。
6. 自定义 Spring Security Filter 通过单次 Lua 原子校验会话并按规则续期，再检查用户状态、加载权限并建立 `Authentication`。
7. 方法级权限注解执行功能权限校验；管理类业务在 Service 层执行越权防护校验。
8. 退出登录时删除会话；用户禁用/删除/改密/重置密码时在同一事务内递增 `auth_version`，旧会话在请求期被拒绝。

组件职责：

- `SecurityConfig`：SecurityFilterChain、无状态配置、异常处理、公开端点规则、`AuthenticationManager`。
- `AuthController`：登录、退出、保活、修改本人密码、当前用户等 HTTP API。
- `AuthService`：登录编排，拆为 `verifyCredentials`（限流 → 认证 → 状态复核）与 `establishSession`（建会话 → 记录登录信息 →
  审计）两个阶段；同时负责修改本人密码。
- `LoginRateLimiter`：登录尝试原子准入与成功建会话额度限制。
- `TokenService`：生成随机 Token、计算 Token 摘要。
- `SessionService`：创建会话、认证并续期（单次 Lua）、撤销单个会话（删除 Key）。
- `TokenAuthenticationFilter`：解析 Bearer Token，验证会话并设置 SecurityContext，处理强制改密限制。
- `LoginService`：登录时从数据库主库直接读取用户（不走缓存），同一次读取返回 `status`、`auth_version` 与
  `must_change_password`，避免登录响应或新会话使用多次读取拼出的不一致快照。
- `UserCacheService`：用户基础信息缓存。
- `RolePermissionService`：角色权限缓存与有效权限加载；另提供「直接读库计算有效权限」的方法供越权校验使用。
- `PermissionChecker`（Bean 名 `perm`）：授权判断入口。
- `AdminPrivilegeGuard`：管理操作越权防护（8.6）。
- `AuthVersionService`：`bumpAuthVersion(uid)`，在调用方的数据库事务内原子递增用户 `auth_version`；要求处于活动事务，否则抛异常。
- `AuthCacheInvalidator`：事务提交后失效用户缓存与角色权限缓存；提供统一的事件发布入口，要求调用时处于活动事务，否则抛异常。
- `AuthAuditService`：审计事件（若项目已有审计机制则复用）。
- `AuthMetrics`：认证指标埋点。
- `AuthenticationEntryPoint` / `AccessDeniedHandler`：统一返回 401 / 403。

Agent 应先检查现有模块、统一响应体、异常体系、Redis 封装、密码编码器、审计机制、指标体系和数据库实体，复用已有实现，避免重复建设。

### 3.1 每请求性能预算（必须遵守）

正常的已认证请求应保持以下固定路径，不允许为认证版本校验新增逐请求数据库查询：

1. `SessionService.authenticateAndRenew`：一次单 Key Lua 调用，完成会话存在性、绝对过期与 TTL 续期判断。
2. `UserCacheService`：一次用户缓存 `GET`。
3. `RolePermissionService`：一次角色权限 `MGET`（无角色时可跳过）。

因此正常请求为约 **3 次 Redis 网络往返、0 次数据库查询**；不含业务 Controller 自身的数据访问。不得在 Filter、
`PermissionChecker` 或每次授权表达式中单独查询数据库。角色缓存未命中时，按缺失角色 ID
批量查询数据库后回填；同一进程使用单飞/分段锁减少重复回源。只有缓存未命中、用户缓存版本落后于会话版本、登录或管理越权校验等特殊路径才访问数据库。

不得为了缩短用户撤销窗口而默认开启每请求查库。当前选择是：稳定的请求开销 + 最多约 66s 的用户状态撤销窗口 + 最多约 5.5m
的角色权限旧缓存窗口。

## 4. Token 设计

### 4.1 生成规则

- 使用 `SecureRandom` 生成 32 字节随机数（256-bit 熵），URL-safe Base64 编码并去掉填充符（结果为 43 个字符）。
- Token 是不透明随机字符串，不承载用户 ID、角色或权限等业务信息。
- 不允许使用 UUID、时间戳、用户名拼接或普通随机数作为 Token。
- Token 仅在登录成功响应中返回给客户端。

```java
private final SecureRandom secureRandom = new SecureRandom();

public String generateToken() {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
}
```

`sessionId` 不是凭据，仅用于日志、审计与未来的会话管理，可使用 `UUID.randomUUID()`，但绝不能用它做认证。

### 4.2 存储原则

- Redis 不保存原始 Token，以其 SHA-256 摘要（小写十六进制）作为 Key 的一部分。
- 请求到达时对客户端 Token 重新计算摘要后查找会话。
- Token 熵为 256-bit，无需加盐或 HMAC；摘要只用于抵御 Redis 数据泄露后的直接复用。
- Token 必须通过 HTTPS 传输，不得写入日志（普通、异常、审计），不得通过 URL query 传输。

## 5. 会话设计

### 5.1 Redis Key

统一前缀：`rooe:auth`。所有 Key 必须设置 TTL。

| Key                                                        | 类型           | 值                | TTL                              |
|------------------------------------------------------------|--------------|------------------|----------------------------------|
| `rooe:auth:session:{tokenDigest}`                          | Hash         | 会话字段（见 5.2）      | 滑动，≤ 距绝对过期剩余时间                   |
| `rooe:auth:user:{uid}`                                     | String(JSON) | 用户基础信息（见 9.1）    | `auth.cache.user-ttl`            |
| `rooe:auth:role-permissions:{roleId}`                      | String(JSON) | 角色信息与权限集合（见 9.2） | `auth.cache.role-permission-ttl` |
| `rooe:auth:login-attempt:user:{sha256(canonicalUsername)}` | String(计数)   | 账号登录尝试额度         | `login-limit.window`             |
| `rooe:auth:login-attempt:ip:{trustedIp}`                   | String(计数)   | IP 登录尝试额度        | `login-limit.window`             |
| `rooe:auth:login-ok:user:{sha256(canonicalUsername)}`      | String(计数)   | 成功建会话额度          | `login-limit.success-window`     |
| `rooe:auth:password-attempt:user:{uid}`                    | String(计数)   | 修改本人密码时旧密码校验额度   | `password-limit.window`          |

本方案的每个 Lua 脚本都只访问单个 Key（计数器脚本也是），会话撤销不依赖 Redis 内的跨 Key 操作（见 5.5）。

### 5.2 会话字段

| 字段                 | 说明                                                                     |
|--------------------|------------------------------------------------------------------------|
| `sessionId`        | 服务端生成，不接受客户端指定                                                         |
| `userId`           | 用户 ID                                                                  |
| `createdAt`        | 登录时间（epoch 秒），不可变                                                      |
| `absoluteExpireAt` | `createdAt + max-lifetime`（epoch 秒），不可变                                |
| `authVersion`      | 登录时用户的 `auth_version`（来自 `verifyCredentials` 的同一次数据库读取），不可变；撤销依据，见 5.5 |
| `clientType`       | 客户端类型（可空），为将来的会话列表预留，本期仅写入                                             |
| `loginIp`          | 登录 IP（按 6.2 的可信代理规则解析），仅写入，不用于认证决策                                     |
| `userAgent`        | 登录时的 `User-Agent`，截断至 200 字符，仅写入，不用于认证决策                               |

登出时删除 Key，不使用 `status` 字段。Redis 正常且删除写入未丢失时立即撤销；故障切换丢失 `DEL` 时可能在剩余 TTL 内复活，见
5.5。`loginIp`、`userAgent` 属于个人信息，不得写入日志和指标标签。

**关于用户会话索引：**本方案没有 `user-sessions` 索引，用户级撤销依赖 `auth_version`（5.5）；普通登出仍是单会话 Redis `DEL`
，因此索引不承担任何安全职责。将来做会话列表和踢人时，再增加管理用的索引（建议 Hash：`user-sessions:{uid}`，
`sessionId → tokenDigest`），它丢失或不准确只影响列表展示，不影响安全。由于会话最长存活 `max-lifetime`，索引上线后至多经过
`max-lifetime`，所有存活会话都会在索引覆盖范围内，不需要数据迁移。踢出单个会话是删除其会话 Key，属于 Redis 级别的撤销，不受
`auth_version` 保护；若将来需要与按用户撤销同等的强度，再单独设计单会话撤销标记。

### 5.3 配置

必须配置化，不要硬编码。启动时校验 `max-lifetime > idle-timeout > 0`。本期不配置 `renew-min-interval`，也不实现续期节流：每个允许续期的请求都刷新
TTL，管理后台流量下可以接受；将来若需要，只能在 Lua 脚本内按 TTL 节流，以保持原子性。

```yaml
auth:
  session:
    idle-timeout: 30m          # 服务端空闲超时
    max-lifetime: 12h          # 从登录开始计算的最大存活时长
    renew-exclude-paths: # 不触发续期的路径（Ant 风格），按项目接口清单填写
      - /api/auth/logout
      - /actuator/**
      # Agent 应从接口清单识别后台轮询/健康检查接口；不确定项在汇总中列出
  cache:
    user-ttl: 60s              # 用户缓存承载 status/auth_version 校验，绝对截止时间从主库读取开始时计算
    role-permission-ttl: 5m
  login-limit:
    window: 15m
    max-attempts-per-account: 60
    max-attempts-per-ip: 200
    success-window: 10m
    max-successes-per-account: 30
  password-limit:
    window: 15m
    max-attempts-per-user: 10
  super-admin-role-code: SUPER_ADMIN
```

`/api/auth/touch` 不得放入 `renew-exclude-paths`。配置校验还应检查 `idle-timeout`、`max-lifetime` 均为正数。

### 5.4 生命周期与原子性要求

所有会话操作统一封装在 `SessionService`。Redis 脚本使用 `StringRedisTemplate` 的 `DefaultRedisScript`
（或项目现有等价方式）执行；每个脚本只访问**单个 Key**。会话撤销不依赖 Redis 内的跨 Key 操作（见 5.5），因此本节没有用户索引维护、批量撤销或跨
Key 原子性的要求。禁止在 Java 中把需要原子完成的多条命令拆开执行。

**时间来源：**所有脚本所需的当前时间 `now`（epoch 秒）由应用通过 `ARGV` 传入，统一来自注入的 `Clock`，不在脚本内调用
`redis.call('TIME')`。这样 `now` 与应用生成的 `createdAt`、`absoluteExpireAt` 处于同一时钟域，测试时也可以固定时间。多实例之间的时钟偏差由
NTP 保证（1.3）。

#### 创建会话（登录成功）

通过单个 Lua 脚本（或单 Key 的 MULTI/EXEC）原子完成：

1. 写入 `rooe:auth:session:{tokenDigest}` Hash，字段符合 5.2（含 `authVersion`）。
2. 设置 TTL 为 `min(idle-timeout, absoluteExpireAt - now)`。

必须原子，避免留下没有 TTL 的会话 Key。脚本执行前，应用生成 Token、摘要、`sessionId`、`createdAt`、`absoluteExpireAt`，并取得
`authVersion`（登录时从数据库读取，6.1）；客户端不能指定这些值。脚本成功后才向客户端返回 Token。Redis 命令失败则登录失败并返回
503，不返回一个未被服务端持久化的 Token。

单个用户的会话数量由「成功登录频率限制」（6.2）约束，没有硬上限；不维护用户会话索引。

#### 认证并续期

Filter 解析出 `digest` 后，**第一步**调用 `SessionService.authenticateAndRenew(digest, renewAllowed)`，不再有独立的
`readSession` 和二次校验。**会话存在性检查、绝对过期检查和 TTL 刷新必须在同一个 Lua 脚本中完成**，避免「先读取、再续期」与并发登出发生竞态。
`renewAllowed` 为 false（命中 `renew-exclude-paths`）时，脚本只做存在性与绝对过期检查，不修改 TTL。

脚本语义：

1. 会话 Key 不存在：返回 `NOT_FOUND`，认证失败。
2. 读取 `absoluteExpireAt`；`now` 大于或等于该值：删除会话 Key，返回 `EXPIRED`。
3. 若 `renewAllowed=false`，返回会话字段，`renewed=false`，不修改 TTL。
4. 若允许续期，计算 `targetTtl = min(idleTimeoutSeconds, absoluteExpireAt - now)`；结果小于等于 0 则删除并返回 `EXPIRED`
   ；否则对仍存在的会话 Key 设置该 TTL，返回会话字段、`renewed=true` 和新的剩余 TTL（秒）。
5. 脚本不创建会话 Key。已经被删除的会话不能通过续期逻辑复活。

Java 侧根据脚本结果建立 `SessionInfo`（含 `authVersion`、`renewed` 与 `ttlSeconds`）。Redis 脚本异常必须向上抛出，由认证过滤器返回
503；不能降级为匿名后继续放行。

**先续期、后做用户校验：**用户被禁用/删除或会话版本落后时，其会话可能在被拒绝前被续期一次；强制改密受限会话访问不允许的路径收到
403 时也会续期。这些都没有实际影响：前者随即被删除，不会延长任何有效访问，后者本来就是已认证用户的正常请求。

> Lua 只保证脚本执行期间 Redis 内部操作的原子性。会话是否被撤销由用户的 `auth_version` 与状态决定（5.5、7.2）。

#### 单会话撤销

`revokeByDigest(digest)` 就是对会话 Key 的一次 `DEL`，天然原子且幂等；Key
不存在也视为成功。登出、绝对过期，以及请求期校验拒绝（版本落后、用户已禁用或删除）都使用它。不需要读取 `userId`，也没有索引需要维护。

#### 续期与登出并发

续期脚本与 `DEL` 都是 Redis 单线程内的原子操作，因此只有两种顺序：续期先执行、随后 `DEL` 删除会话；或 `DEL` 先执行、续期发现
Key 不存在并返回 `NOT_FOUND`。禁止通过 `EXPIRE` 对不存在的 Key 进行创建，也不得用普通写命令重建已删除的会话。

#### 通用规则

1. 会话 TTL 永远不超过 `absoluteExpireAt - now`，绝不突破最大存活时长。
2. 只有会话存在且未绝对过期才可认证；请求允许续期时，由同一次脚本刷新 TTL。
3. 命中 `renew-exclude-paths` 的请求只做会话有效性检查，不续期。`/api/auth/touch` 不在排除列表中。
4. 不需要定时任务扫描过期会话，也不需要定时心跳。
5. 会话续期成功时 Filter 设置 `X-Session-Expires-In` 响应头（7.2）；它只表明认证阶段已刷新 TTL，不保证下游 Controller
   成功。前端仅在最终响应为 2xx 且存在该头时更新有效保活时间（12.2）。

### 5.5 会话撤销与保证边界

本方案有两种不同的撤销机制，不能混为一谈：

**A. 用户级撤销（禁用、删除、改密、重置密码、将来的强制下线）**

- 权威状态是数据库用户表的 `auth_version`（单调递增，初始为 0）。触发业务在**同一个数据库事务**内执行
  `UPDATE user SET auth_version = auth_version + 1 ...`；`AuthVersionService.bumpAuthVersion(uid)`
  必须验证当前处于活动事务，未处于事务时抛异常。对硬删除用户，先在同一事务内递增版本，再执行删除。
- 登录时从主库同一次读取 `status` 与 `auth_version`，将版本写入会话的 `authVersion`。
- 请求时读取用户缓存；用户状态不可用或用户不存在时拒绝。若 `session.authVersion < user.authVersion`，会话版本落后，删除会话
  Key 并返回 401。若 `session.authVersion > cachedUser.authVersion`，缓存可能落后于新会话：不得直接使用缓存中的
  `mustChangePassword` 等字段，必须绕过缓存回源主库并修复缓存；主库版本高于会话版本则拒绝，主库版本相等才可继续。若主库版本低于会话版本，按数据不一致处理并
  fail closed（拒绝请求、记录 ERROR 与指标），不得尝试放行。
- 所有认证关键回源读取必须访问主库/权威读，不得使用延迟只读副本或 ORM 查询缓存。
- 用户缓存 TTL 默认 60s、±10%，但**缓存绝对截止时间在发起数据库读取之前确定**；回源后只写入剩余 TTL。缓存写入通过单 Key 原子
  `SET NX PXAT cacheDeadlineEpochMillis`
  完成，避免旧加载结果覆盖已经写入的新版本。即使旧请求在缓存删除之后才回填旧数据，其缓存也不能延长到“数据库读取开始时间 +
  缓存 TTL”之后；读取耗时超过截止时间则不回填。由此约束缓存删除失败、进程崩溃或并发旧值回填时的旧用户状态窗口：最多约
  66s，另受应用与 Redis 的最大时钟偏差影响；从变更提交时算起的上限成立的前提是各节点保持 NTP 同步；更早启动的回源会更早到期。
- 事务提交后 `AuthCacheInvalidator`
  尝试删除用户缓存。删除成功且没有并发旧值回填时，后续请求立即看到新数据；并发旧值回填时仍按上述绝对截止时间兜底。事务提交前已经开始处理的请求可能继续完成，本方案不承诺中断已在途请求。
- 启用用户、用户角色关系变更、角色权限变更不递增 `auth_version`。角色/权限有效性由对应角色权限缓存控制，见 9.5。

| 事件                       | 动作                                                                                                                         | 保证边界                                                                                        |
|--------------------------|----------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| 用户禁用、删除、改密、重置密码或强制设置改密标记 | 事务内递增 `auth_version`；修改/重置密码同时更新密码哈希；重置密码同时将 `must_change_password` 置为 true；任何将该标记从 false 改为 true 的强制改密操作都必须递增版本；提交后删除用户缓存 | 读到新版本后立即拒绝；缓存删除失败或并发旧值回填时，旧缓存最多约 66s 后到期。已经在途的请求可能完成                                        |
| 登录与撤销并发（登录已读取旧版本）        | 登录会话保存读取时的版本                                                                                                               | 后续请求读到新版本后拒绝；缓存仍旧时最多存在约 66s 的撤销窗口                                                           |
| 禁用后再启用                   | 禁用时已经递增版本；启用不回退版本                                                                                                          | 禁用前的旧会话不会因重新启用而复活                                                                           |
| 用户退出登录                   | 对当前会话 Key 执行 `DEL`，幂等                                                                                                      | Redis 正常且没有写丢失时立即失效；若主从切换丢失 `DEL`，该 Token 可能复活，并可在会话剩余 TTL 内再次被使用。`auth_version` 不保护普通单会话登出 |
| Redis 主从切换导致会话 Key 复活    | 无额外操作                                                                                                                      | 用户级撤销的会话因版本落后仍会被拒绝（前提是请求读到权威数据库新版本）；普通登出被复活的会话不在此保证内                                        |
| 用户角色/角色权限变更              | 不递增 `auth_version`，提交后失效相关缓存                                                                                               | 若缓存失效失败/旧值并发回填，旧权限最多保留至角色缓存绝对截止时间，默认约 5.5m                                                  |

缓存失效重试 3 次，仍失败则记录 ERROR、增加 `auth_cache_invalidate_failure_total` 并告警。事务提交后的 Spring
事件是进程内机制，无法覆盖“提交后、监听器执行前进程崩溃”；本期接受这个边界，不得声称事件可靠投递。

**性能决策：**本期不提供每请求查库模式。正常请求通过 Redis
缓存校验用户状态和版本，避免给每个请求增加数据库查询；只有缓存未命中或发现缓存版本小于新会话版本时才回源主库。代价是用户级撤销存在最多约
66s 的缓存窗口。若未来要求严格实时撤销，应另行设计明确的一致性与容量方案，不能简单地默认给每个请求增加数据库查询。

登出时若 Redis 不可用，返回 503，不得声称服务端撤销已经完成。前端应清理本地 Token，但该动作不等于服务端确认撤销。

### 5.6 时间与时钟

通过注入 `Clock` 获取当前时间，便于测试；Lua 脚本所需的 `now` 一律由应用经 `ARGV` 传入（5.4）；`createdAt` 与
`absoluteExpireAt` 均存 epoch 秒，由应用 `Clock` 生成，不使用数据库或 Redis 的时钟；`auth_version`
是单调递增整数，不依赖时钟；多实例部署要求服务器时间同步（NTP）。空闲过期由 Redis TTL 负责，绝对过期在 Lua 脚本中校验。

### 5.7 Redis 部署要求

认证会话由 Redis 承载，Redis 的行为直接决定会话是否存在；用户级撤销由数据库 `auth_version` 兜底，普通登出仍依赖会话 Key
的删除，因此 Redis 仍需稳定可用，因此生产部署应满足以下要求；本期由 Agent 检查并输出状态，除非具备明确权限与环境支持，否则不直接修改部署环境：

1. **禁止淘汰**：认证数据所在实例设置 `maxmemory-policy noeviction`。本方案所有 Key 都带 TTL，若使用 `volatile-*` 或
   `allkeys-*` 策略，内存紧张时会静默淘汰会话，表现为用户随机掉线。若该实例同时承载依赖淘汰策略的业务缓存，则认证数据必须拆到独立实例（Redis
   Cluster 不支持多 db，不能靠 db 编号隔离）。`noeviction` 下内存写满会导致写命令失败，按 D11 返回 503（fail closed），这是预期行为。
2. **容量与告警**：单个会话约几百字节，按在线用户数估算容量；内存使用率超过 70% 告警；`evicted_keys` 指标必须恒为 0，大于 0
   立即告警。
3. **持久化**：至少开启 AOF（`appendfsync everysec`），避免 Redis 重启导致全员登出。
4. **高可用与部署形态**：生产环境使用哨兵或云托管主备版高可用，**本方案不使用 Redis Cluster**（D20）。当前所有 Lua 脚本都只访问单个
   Key，技术上不被 Cluster 阻塞（角色缓存的多 Key 读取需改为 pipeline 或客户端自动拆分）；保持非 Cluster
   是为了减少运维与未来会话管理的复杂度，且管理后台当前无需分片；这不是因为当前单 Key Lua 无法运行在 Cluster 上。**主从切换的影响
   **：主从为异步复制，故障切换时最近约 1 秒的写入可能丢失，包括会话创建、续期和登出的 `DEL`
   ，可能导致刚登录的用户被登出，或刚登出的会话复活；禁用、删除、改密后的旧会话在请求读取到数据库较新的 `auth_version`
   后仍会被拒绝；普通登出被复活的会话不受 `auth_version` 保护（5.5）。刚登出的会话若因丢写复活，可能在剩余 TTL
   内再次使用；本期明确接受此故障边界。`WAIT 1 <timeout>` 只能提高复制确认程度，不能提供强一致保证，也不作为本方案的安全保证。
5. **访问控制**：应用使用 ACL 专用账号，限定 Key 前缀 `rooe:auth:*` 和所需命令，禁用 `FLUSHALL`、`FLUSHDB`、`KEYS`、`CONFIG`
   等危险命令；启用密码认证；跨网络访问时启用 TLS；不得暴露到公网。

Redis、应用服务器均须保持 NTP 时间同步，因为会话绝对过期和缓存 `PXAT` 采用 epoch 时间。缓存截止时间必须在主库查询开始前计算，并通过
`PXAT` 指定，不得在回填时重置 TTL。Agent 无法修改部署环境时，应在汇总中输出对照以上五项的检查清单与当前状态，由运维确认。

#### 5.7.1 当前环境检查结果（Redis `192.168.1.4:6379`）

| 项     | 要求                               | 实测                                                              | 状态                          |
|-------|----------------------------------|-----------------------------------------------------------------|-----------------------------|
| 淘汰策略  | `noeviction`                     | `maxmemory-policy noeviction`，`maxmemory_human 0B`（未设上限）        | 已验证（策略符合）                   |
| 部署形态  | 单分片、非 Cluster                    | `redis_mode:standalone`，`cluster_enabled:0`，`info keyspace` 单实例 | 已验证                         |
| 版本    | ≥ 6.2（`SET NX PXAT`、单 Key Lua）   | `redis_version:8.6.3`                                           | 已验证                         |
| 容量与告警 | 使用率 > 70% 告警，`evicted_keys` 恒为 0 | `used_memory_human 1.87M`；本轮跑测后 `rooe:auth:*` 无残留 Key           | 部分验证（容量占用低、无残留；告警阈值需运维配置监控） |
| 持久化   | 至少 AOF `appendfsync everysec`    | 未检查（需运维用 `CONFIG GET appendonly/appendfsync` 或托管平台确认）           | 未验证                         |
| 高可用   | 哨兵或云托管主备                         | 未检查（`standalone` 形态本身不含哨兵/主备信息，需运维确认）                           | 未验证                         |
| 访问控制  | ACL 专用账号、限定前缀与命令、TLS、不暴露公网       | 当前使用密码认证且连接可达；ACL/TLS/网络隔离未检查                                   | 未验证                         |
| 时间同步  | 应用与 Redis 均 NTP 同步               | 未检查（需运维确认）                                                      | 未验证                         |

说明：本轮只做了只读探测与集成测试联调，未修改任何 Redis 或 MySQL 部署配置。表中「未验证」项需运维按第 5.7 节要求确认；
`noeviction` 下内存写满会按 D11 返回 503（fail closed），属于预期行为。

## 6. 登录与账号保护

### 6.1 流程

`AuthService` 拆成两个阶段，便于将来在两个阶段之间插入 MFA；不要在其他位置假设「密码通过即建会话」。

**阶段一：`verifyCredentials`**

1. 校验请求参数（用户名长度上限；密码长度上限由项目密码策略决定，注意 BCrypt 只使用前 72 字节）。
2. 登录尝试准入（6.2）：按账号与 IP 原子预占固定窗口额度；任一维度超限即返回 429，不进行密码校验。
3. 调用 `AuthenticationManager.authenticate(...)`：
    - `LoginService` 从数据库直接读取用户，不使用缓存；同一次读取返回 `status` 与 `auth_version`，后续步骤使用这份快照。
    - 用户不存在与密码错误统一抛出 `BadCredentialsException`。保持 `hideUserNotFoundExceptions` 默认值，
      `DaoAuthenticationProvider` 会对不存在的用户执行一次哈希比对以抵消时序差异。
    - `LoginCredentialSnapshot` 的 enabled / accountNonLocked 恒为 true，用户状态检查推迟到第 5
      步，避免在密码验证前泄露账号状态；同一次查询快照同时携带
      `status`、`auth_version`、`must_change_password`。
4. 认证失败：记录审计与指标，返回统一的「账号或密码错误」（HTTP 401）。尝试额度已经在密码校验前原子预占，不在失败后另行递增。
5. 认证成功：使用第 3 步同一次数据库读取的快照复核用户状态；已禁用则返回明确的「账号已禁用」业务错误（HTTP
   401，不同的业务错误码）。此时调用者已证明知道密码，不构成账号枚举。

**阶段二：`establishSession`**

6. 成功建会话频率检查（6.2）：通过原子额度脚本校验并占用成功额度；超限返回 429，不创建会话。随后生成 Token 与 sessionId，用第 3
   步快照中的 `auth_version`、客户端类型、登录 IP、截断后的 `User-Agent` 创建会话（5.4）。
7. 读取该用户登录前的 `last_login_at`、`last_login_ip` 用于响应，再更新为本次登录时间和 IP。该更新为尽力而为，失败只记录警告，不影响登录。
8. 记录 `LOGIN_SUCCESS` 审计和指标。
9. 返回登录结果，响应头加 `Cache-Control: no-store` 与 `Pragma: no-cache`。

登录结果预留 `status` 字段，本期恒为 `SUCCESS`；将来可扩展为 `MFA_REQUIRED` 等并返回挑战信息。客户端遇到未知的 `status`
应视为登录未完成。

密码禁止明文存储或日志输出，推荐 BCrypt 或项目已采用的安全 `PasswordEncoder`。

### 6.2 登录限流

所有登录请求在密码校验前执行账号与 IP 两个维度的尝试准入。它统计**全部登录尝试**
，而不是只统计失败次数，避免并发请求在“检查失败计数”和“失败后递增”之间穿透限流。

- 默认窗口：`login-limit.window=15m`；单账号最多 60 次尝试，单 IP 最多 200 次尝试。具体阈值均配置化，需结合后台用户规模调整。
- Key：`rooe:auth:login-attempt:user:{sha256(canonicalUsername)}`、`rooe:auth:login-attempt:ip:{trustedIp}`。账号 Key
  必须使用与登录认证完全相同的规范化规则，避免认证与限流规则不一致导致绕过；若项目没有既有规范，统一规定为 trim
  后小写，并让登录查询与限流共用该规范。IP 只取可信代理解析后的地址。
- 每个维度由一个只操作单 Key 的 Lua 脚本执行原子准入：Key 不存在时写入计数并设置固定窗口
  TTL；计数低于上限时原子递增并放行；已达到上限时不再增加计数，返回拒绝和剩余 TTL。禁止先 `GET` 再在 Java 侧决定是否 `INCR`
  。账号先预占额度后若 IP 超限，该账号额度不退回，属于有意的保守策略。
- 任一维度拒绝时返回 429 与 `Retry-After`，不进行密码校验。账号与 IP 任一维度超限都记录 `LOGIN_RATE_LIMITED`
  ；限流计数本身不作为用户名是否存在的信号。
- 成功建会话另有独立额度：每账号 `success-window=10m` 内最多 30 次。认证通过后、创建会话前，由单 Key Lua 原子执行“检查额度 +
  占用额度 + 首次设置 TTL”。额度已满时不创建会话，返回 429。这样并发登录也不能超过该窗口额度。
- 不维护失败后专用计数 Key；错误凭据仍须记录 `LOGIN_FAILURE` 审计和 `auth_login_total{result=bad_credentials}`
  指标，但不得为每次失败再产生一套计数器。
- 账号限流可能被恶意请求用于临时阻止合法用户登录，这是本期接受的取舍；后续可增加验证码、风险识别或账号/IP
  组合策略，但不应在本期引入复杂锁定状态机。
- Redis 不可用时登录返回 503。所有限流脚本必须真实 Redis 并发测试，验证并发情况下准入数不会超过配置上限。

限流 Key 也必须有 TTL。首次创建计数器与设置 TTL 必须在同一 Lua 脚本内完成；窗口内后续请求不得重置 TTL，因此这是固定窗口，不是滑动窗口。

### 6.3 审计事件

至少记录：`LOGIN_SUCCESS`、`LOGIN_FAILURE`（原因码；用户名/IP 仅在专用受控审计渠道按需记录）、`LOGIN_RATE_LIMITED`、`LOGOUT`、
`SESSION_REVOKED`（原因：登出/绝对过期/版本落后/用户已禁用或删除）、`AUTH_VERSION_BUMPED`（原因：用户禁用/删除/改密/重置密码/强制改密）、
`PASSWORD_CHANGED`、角色权限与用户角色变更、`PRIVILEGE_DENIED`（8.6 的越权拦截，含规则编号）。

审计内容不得包含密码、原始 Token、Token 摘要。若项目已有审计机制则复用，否则使用独立的结构化日志（如专用 logger`AUTH_AUDIT`）。

### 6.4 指标与告警

使用项目现有的指标体系（默认 Micrometer），指标标签不得包含用户名、uid、IP 等高基数或敏感值：

| 指标                                      | 标签                                                                                                | 说明                                 |
|-----------------------------------------|---------------------------------------------------------------------------------------------------|------------------------------------|
| `auth_login_total`                      | `result`：`success` / `bad_credentials` / `disabled` / `rate_limited`                              | 登录结果                               |
| `auth_request_rejected_total`           | `status`：`401` / `403` / `503`                                                                    | 认证与鉴权拒绝                            |
| `auth_session_renew_total`              | —                                                                                                 | 实际成功刷新会话 TTL 的次数                   |
| `auth_session_rejected_total`           | `reason`：`version_stale` / `user_disabled` / `user_missing` / `auth_version_inconsistent`         | 请求期校验拒绝了版本落后、用户已禁用/不存在或版本数据不一致的会话  |
| `auth_version_bump_total`               | `reason`：`disabled` / `deleted` / `password_changed` / `password_reset` / `force_password_change` | 认证版本递增次数                           |
| `auth_redis_error_total`                | `op`：`session` / `cache` / `limiter`                                                              | Redis 异常                           |
| `auth_cache_invalidate_failure_total`   | —                                                                                                 | 缓存失效最终失败                           |
| `auth_user_cache_version_refresh_total` | —                                                                                                 | 用户缓存版本低于会话版本，触发了少量的主库刷新；正常请求不应持续增长 |
| `auth_privilege_denied_total`           | `rule`：`R1`…`R6`                                                                                  | 越权防护拦截                             |

建议告警：登录失败或限流计数短时突增；`503` 持续出现；`auth_cache_invalidate_failure_total` 增长；Redis `evicted_keys` >
0；Redis 内存使用率 > 70%；Redis 发生主从切换。

### 6.5 修改本人密码与强制改密

**修改本人密码** `POST /api/auth/password`（`isAuthenticated()`）：

- 请求：`oldPassword`、`newPassword`。
- 先检查独立的本人改密额度：`password-limit.window`（默认 15m）内每个用户最多 10 次尝试，按已认证的 `uid` 计数，通过单 Key
  Lua 原子准入；超限返回 429，不再校验旧密码，防止持有会话的攻击者无限次猜测旧密码。该额度不复用登录尝试计数。
- 旧密码用 `PasswordEncoder.matches` 校验；失败记录审计与指标，返回 400 与业务码 `AUTH_OLD_PASSWORD_INCORRECT`，不使用
  401，避免前端误清登录状态。
- 新密码遵循项目密码策略；项目没有策略时，默认要求长度 ≥ 8，且不得与用户名或旧密码相同。若项目使用 BCrypt，编码前必须校验密码
  UTF-8 编码不超过 72 字节，超出时明确拒绝，不得静默截断；登录、修改、重置密码共用同一套长度与编码规则。
- 成功：在同一事务内更新密码哈希、递增 `auth_version`（`bumpAuthVersion`），`must_change_password` 置为
  false；事务提交后删除用户缓存，记录审计；返回 204。所有旧会话（含当前会话）在请求读到新版本后被拒绝；缓存并发回填时存在最多约
  66s 的有界窗口（见 5.5）。客户端清理本地状态，要求重新登录。
- 若项目已有等价接口，复用并接入上述撤销与标记逻辑，不重复建设。

**强制改密**：

- `must_change_password = true` 的用户（初始管理员、被管理员重置密码的用户）可以正常登录并建立会话，但处于受限状态：Filter 将其
  authorities 置空，只放行 `PASSWORD_CHANGE_ALLOWED_PATHS`（`/api/auth/me`、`/api/auth/password`、`/api/auth/logout`、
  `/api/auth/touch`），其他请求直接返回 403，业务码 `AUTH_PASSWORD_CHANGE_REQUIRED`。
- 登录响应与 `/api/auth/me` 返回 `mustChangePassword`，前端据此跳转改密页。
- 这一限制由 Filter 按路径强制执行，不依赖各接口上的注解，避免遗漏的 `isAuthenticated()` 接口被绕过。

## 7. Spring Security 配置

### 7.1 核心配置要求

- 使用 `SecurityFilterChain`，不使用已废弃的 `WebSecurityConfigurerAdapter`。
- `SessionCreationPolicy.STATELESS`；禁用表单登录、HTTP Basic 和默认 logout。
- 关闭 CSRF（D6），并在配置处注释原因。
- CORS 使用项目统一的 `CorsConfigurationSource`，`http.cors(...)` 接入；只允许明确的前端来源、方法和请求头（含
  `Authorization`、`Content-Type`）。验证未携带 Token 的 OPTIONS 预检请求能返回正常 CORS 响应头。同时通过 `exposedHeaders`暴露
  `X-Session-Expires-In`，前端需要读取它（12.2）。
- 开启 `@EnableMethodSecurity`。
- 定义一个公开端点常量 `PUBLIC_PATHS`，至少包含：登录接口、健康检查、`/error`。`/error` 必须放行，否则 Spring Security 6 会对
  ERROR 分发再次鉴权，真实异常被 401/403 掩盖。
- 其余请求 `anyRequest().authenticated()`。不要将 `/api/**` 无差别放行。
- `TokenAuthenticationFilter` 以 `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)` 加入。不要把它声明为
  `@Component`，否则会被 Servlet 容器额外注册一次；确需声明时用 `FilterRegistrationBean` 将其 `setEnabled(false)`。
- 暴露 `AuthenticationManager`（包含 `DaoAuthenticationProvider`）供登录使用。
- `AuthenticationEntryPoint` 返回 401，并带 `WWW-Authenticate: Bearer`；`AccessDeniedHandler` 返回 403。响应体沿用项目统一响应体。

### 7.2 Filter 行为

`TokenAuthenticationFilter`（`OncePerRequestFilter`）：

1. 请求命中 `PUBLIC_PATHS`（`shouldNotFilter`）时直接跳过。
2. 读取 `Authorization`；没有 Bearer Token 时继续过滤链（匿名），由授权规则决定返回 401。
3. Token 格式不合法（非 43 字符 URL-safe Base64）时不建立身份，继续过滤链。
4. 计算摘要；根据请求是否命中 `renew-exclude-paths` 确定 `renewAllowed`；调用
   `SessionService.authenticateAndRenew(digest, renewAllowed)`（单次 Lua，5.4）。返回 `NOT_FOUND` 或 `EXPIRED`
   时不建立身份，继续过滤链（匿名）。
5. 通过 `UserCacheService.getUserForSession(userId, sessionAuthVersion)` 读取已解析用户。正常缓存命中只需一次 Redis `GET`
   ；缓存未命中时回源主库；若缓存里的 `authVersion` 小于本次会话版本，Service 必须绕过缓存读取主库，不能把旧缓存中的`status`
   或 `mustChangePassword` 返回给 Filter。用户不存在、已禁用或已删除时，`revokeByDigest` 并保持匿名，记录拒绝指标与审计。
6. 比较 Service 返回的最终用户版本：若 `session.authVersion < user.authVersion`，会话版本落后，删除会话并按 401
   处理；若两者相等，继续。若 `session.authVersion > user.authVersion`，说明在已绕过低版本缓存后，主库版本仍低于会话版本，按数据不一致
   fail closed，返回 401 并记录 ERROR 与 `auth_session_rejected_total{reason=auth_version_inconsistent}`
   。正常版本匹配路径不查数据库（5.5、9.1）。
7. 第 4 步返回 `renewed=true` 时，Filter 可设置 `X-Session-Expires-In: <剩余空闲秒数>`。该头表示会话 TTL 已刷新，不保证下游
   Controller 最终成功；因为 Filter 先于方法鉴权运行，后续 403 或 5xx 响应也可能带该头。被排除续期的请求不带该头。前端只能在最终状态为
   2xx 且存在该头时更新有效保活时间。
8. 若用户 `mustChangePassword = true`：authorities 置空；请求路径不在 `PASSWORD_CHANGE_ALLOWED_PATHS` 时，直接返回 403（业务码
   `AUTH_PASSWORD_CHANGE_REQUIRED`），不继续过滤链；在允许路径内则正常继续。
9. 否则通过 `RolePermissionService` 加载有效权限，构造 `GrantedAuthority`：每个权限码一个 authority，每个启用角色额外一个
   `ROLE_{roleCode}`。
10. 创建 `Authentication`（principal 为不含密码的 `LoginUser`，包含 uid、用户名、显示名、sessionId、mustChangePassword），写入
    `SecurityContextHolder`，继续过滤链。
11. 不把客户端提供的 userId、roleId、权限列表当作可信身份来源。

异常处理：

- 会话 Redis 读写异常：清理 SecurityContext，直接返回 503（统一响应体），不继续过滤链。
- 权限缓存异常：回源数据库；数据库也失败则返回 503。
- 无论哪种失败，都不能保留上一个请求的身份，也不能把基础设施故障伪装成有效认证或普通 401。

## 8. RBAC 与授权

### 8.1 关系

```text
User
  └── UserRole
        └── Role
              └── RolePermission
                    └── Permission
```

权限实体字段：`id`、`permission_code`（唯一，如 `sys:user:list`，命名 `模块:资源:动作`）、`name`、`type`（按钮/API/菜单，按现有模型）、
`status`。后端授权以稳定的权限标识为准，不以菜单是否显示为安全边界。

### 8.2 授权方式

业务接口使用方法级注解，放在 Controller 方法上，同一接口只在一层声明：

```java

@PreAuthorize("@perm.has('sys:user:list')")
@GetMapping
public Result<PageResult<UserVO>> list(UserQuery query) { ...}
```

创建、编辑、删除使用不同权限码，如 `sys:user:create` / `sys:user:update` / `sys:user:delete`。只需登录、无需特定权限的接口显式标注
`@PreAuthorize("isAuthenticated()")`（如 `/api/auth/me`、`logout`、`touch`、`password`）。

```java

@Component("perm")
public class PermissionChecker {
    private final String superAdminAuthority;   // "ROLE_" + auth.super-admin-role-code

    public boolean has(String permissionCode) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || a instanceof AnonymousAuthenticationToken) {
            return false;
        }
        for (GrantedAuthority g : a.getAuthorities()) {
            String v = g.getAuthority();
            if (v.equals(superAdminAuthority) || v.equals(permissionCode)) {
                return true;
            }
        }
        return false;
    }
}
```

URL 级授权只用于 `PUBLIC_PATHS` 与 `authenticated()` 兜底，不再重复实现同一套权限判断。

### 8.3 多角色合并与超级管理员

- 用户拥有多个角色时，有效权限取并集并去重。
- 禁用的角色不参与合并；禁用的权限不进入角色的权限集合（加载时按权限状态过滤）。
- 超级管理员：拥有启用状态的 `super-admin-role-code` 角色的用户，`perm.has(...)` 恒为 true。判断完全基于服务端数据。
- 超管相关的敏感管理操作受 8.6 保护，不依赖 `perm.has`。

### 8.4 默认拒绝的保障（D10）

`@PreAuthorize` 是 opt-in：漏标注解的接口，任何已登录用户都能访问。因此必须增加一个自动化测试，遍历
`RequestMappingHandlerMapping` 中项目基础包下的所有 handler（排除 `BasicErrorController`、actuator 等框架端点），断言每个
handler 满足以下之一，否则构建失败：

- 方法或类上带有 `@PreAuthorize`；
- 其全部 URL 模式都在 `PUBLIC_PATHS` 中。

存量接口接入时，Agent 应逐个确认所需权限码；无法确定的在汇总中列出，不得为通过测试而批量添加 `isAuthenticated()`。

另增加一个一致性测试：所有 `@PreAuthorize` 中引用的权限码，必须存在于权限种子数据中。

### 8.5 异常处理不得吞掉 401/403

方法级鉴权抛出的 `AccessDeniedException` 发生在 Controller 层，会先被项目的 `@RestControllerAdvice` 处理，不会到达
`AccessDeniedHandler`。要求：

- 在统一异常处理中显式处理 `AccessDeniedException`（403）和 `AuthenticationException`（401），响应体与`AccessDeniedHandler` /
  `AuthenticationEntryPoint` 复用同一个响应构造方法，格式一致；
- 或者在这两类异常上直接 rethrow 交给过滤器链处理。

必须有 MockMvc 端到端测试覆盖「已登录、无权限 → 403」。

### 8.6 管理操作的越权防护（D15）

拥有「用户管理」「角色管理」权限的人，可能通过管理接口给自己或他人提权。功能权限校验只回答「能不能调用这个接口」，不回答「能不能这样操作」，因此这些规则必须在业务层显式校验：

| 规则         | 内容                                             |
|------------|------------------------------------------------|
| R1 不得越权分配  | 给用户分配角色（含创建用户时指定角色）：目标角色的有效权限集合必须是操作者有效权限集合的子集 |
| R2 不得越权改角色 | 新增或移除角色的权限关联：变更涉及的每个权限，操作者都必须拥有                |
| R3 不得自改    | 操作者不能修改自己的角色关系，也不能修改自己所拥有角色的权限关联（需由他人操作）       |
| R4 超管角色保护  | 超管角色不可删除、禁用、修改角色码、编辑权限关联；超管角色的授予与回收仅超管可执行      |
| R5 超管账号保护  | 超管账号的禁用、删除、改密/重置密码、角色变更仅超管可执行                  |
| R6 防自锁     | 不能禁用或删除自己；任何操作完成后，系统中必须至少保留一个启用状态的超管账号         |

说明与实现要求：

- R1、R2 对超管豁免（超管本就拥有全部权限），但 R4、R5 仍然适用。
- 由 `AdminPrivilegeGuard` 提供校验方法，在用户管理、角色管理的 Service 方法内、同一事务中显式调用，不依赖注解。
- 所有比较都读取数据库实时计算（使用 `RolePermissionService` 的读库方法），不使用缓存，避免缓存旧数据让越权校验失效。
- R6 的所有可能减少启用超管数量的写操作（禁用/删除超管账号、移除超管角色关系、批量更新用户角色等）都必须在同一事务内先锁定同一条超管角色行（
  `SELECT ... FOR UPDATE`），再检查并执行变更，防止并发路径绕过保护锁。所有代码路径必须遵守同一锁顺序。
- 违反时返回 403：R1–R3 使用业务码 `AUTH_PRIVILEGE_ESCALATION`，R4–R6 使用 `AUTH_PROTECTED_RESOURCE`；同时记录
  `PRIVILEGE_DENIED` 审计（含规则编号）和 `auth_privilege_denied_total` 指标。
- 批量操作逐项校验，任一项违反则整体回滚。
- 对项目已有的用户管理、角色管理接口接入；这些接口尚不存在时，只实现 `AdminPrivilegeGuard` 及单元测试，并在汇总中说明待接入点。

关于 `role_code` 的暴露范围（本期决定：不暴露给客户端）：

- `role_code` 是**服务端内部识别符**，仅用于超管判定与越权校验。鉴权链路从不经过角色出参模型：`PermissionChecker` 依据
  `ROLE_<super-admin-role-code>` 授权位，`TokenAuthenticationFilter` 通过 `enabledRoleCodes` 从服务端数据生成 `ROLE_*`，
  `AdminPrivilegeGuard` 直接读库比较。因此 `RoleDTO`/`RoleAssembler` 不映射 `role_code`，不影响任何功能。
- 不暴露是**有意的安全决定**，不是遗留项：客户端输入一律不可信（见
  8.2/8.3），一旦把超管角色码下发给前端，容易诱导前端据其做本地判断（隐藏/显示按钮等），形成可绕过的假防线；同时会把「哪个角色是超管」这一运维级配置变成界面上可见的攻击目标。
- 当前写入侧也不承载 `role_code`：`RoleCreateRequest`/`RoleUpdateRequest` 及 `RoleAssembler.updateEntity`
  都不含该字段，角色码只能由迁移脚本（见 14.1）与运维配置维护，普通管理接口无法改动，天然满足 R4「不可修改角色码」。
- 若产品将来确需在角色管理界面展示或编辑角色码，须同时做到：仅在 `toDto` 出参映射；把写入收敛为单独的受保护操作并接入 R4
  校验，禁止把普通角色改成保留的超管角色码（唯一索引之外还需应用层校验）；界面对超管角色隐藏编辑入口，与服务端 R4 形成双保险。

## 9. 缓存设计与失效

### 9.1 用户基础信息缓存

`rooe:auth:user:{uid}`，JSON，包含：用户 ID、用户名、显示名称、用户状态、`mustChangePassword`、`authVersion`、角色 ID 列表。部门
ID 等字段仅在系统已有部门模型且本期确有使用时再加。不得保存密码哈希、原始 Token 或不必要的敏感信息。

用户缓存 TTL 默认 60s、±10%。为防止并发回源把旧用户数据在 `DEL` 之后重新缓存并重新获得完整 TTL，必须遵循以下规则：

1. 缓存未命中或缓存版本落后、需要回源时，先用注入的 `Clock` 记录 `loadStartedAt`，并在发起主库查询前确定本次随机 TTL 和
   `cacheDeadline = loadStartedAt + ttl`。
2. 查询必须访问数据库主库/权威读，不使用延迟副本或 ORM 查询缓存。缓存回源应通过独立的短事务读取（Spring 项目优先用单独
   Service Bean 的 `REQUIRES_NEW` + `READ_COMMITTED`，或项目数据库中等价的“读取当前已提交数据”配置），不能复用一个早已建立快照的长事务；
   `loadStartedAt` 必须早于该读事务启动。缓存截止时间一旦确定不得重算。若查询结束时截止时间已过，不回填。
3. 普通缓存未命中时，回填使用单 Key 原子 `SET key value NX PXAT cacheDeadlineEpochMillis`（Redis 6.2+；当前部署基线 Redis
   7.2 可用）。`PXAT` 使用绝对过期时间，避免回填时重新开始完整 TTL；`NX` 确保不覆盖并发写入的新缓存。缓存写入失败不影响当前请求使用刚查到的数据，但必须记录可观测事件。
4. 特殊情况：缓存存在但其 `authVersion` 低于当前会话版本时，`UserCacheService.getUserForSession` 先回源主库，再通过单 Key
   Lua 比较并刷新缓存。仅当 Redis 中仍是本次读取到的旧值，或缓存中的版本仍低于数据库新版本时，才写入新的用户 JSON 与既定的
   `cacheDeadlineEpochMillis`；如果并发进程已写入版本更高或内容不同的同版本缓存，则不覆盖。不能简单执行 `SET NX`，否则旧 Key
   会阻止修复，造成此账号后续每个请求都回源数据库。该路径只发生在缓存版本异常落后时。
5. 变更提交后即使先执行了 `DEL`，先前启动的查询仍可能随后回填旧值；由于截止时间从查询开始时计算，该旧值不会延长至查询开始时间加
   TTL 之后。已在变更提交前开始的在途请求仍可能完成，这是本方案的明确边界。
6. `UserCacheService.getUserForSession(uid, sessionAuthVersion)` 的处理规则是：缓存版本低于会话版本则先回源主库并按第 4
   条尝试修复缓存；缓存版本等于会话版本时正常返回；缓存版本高于会话版本时可直接返回，由 Filter 以版本落后拒绝旧会话。正常版本相同的缓存命中不额外查库。

### 9.2 角色权限缓存

`rooe:auth:role-permissions:{roleId}`，JSON，默认 TTL 5m、±10%。缓存截止时间同样在数据库读取开始前确定，回填使用
`SET key value NX PXAT cacheDeadlineEpochMillis`；读取结束时已过期则不缓存。这样角色权限的旧回源不能在缓存删除后覆盖新缓存或重新获得完整
TTL。

```json
{
  "code": "SUPER_ADMIN",
  "enabled": true,
  "permissions": [
    "sys:user:list",
    "sys:user:create"
  ]
}
```

请求期加载：1 次读用户缓存，1 次 `MGET` 读取全部角色缓存，合计与会话校验续期（单次 Lua）共 3 次 Redis 往返。禁用角色（
`enabled=false`）不贡献权限，也不构成超管。

### 9.3 缓存未命中与故障

- 未命中：通过独立短事务回源主库，回填 Redis；不得复用已建立旧快照的长事务。缓存截止时间必须在读事务启动前确定；缓存未命中使用
  `SET NX PXAT cacheDeadlineEpochMillis`。用户缓存发现版本低于会话版本时，使用 9.1 定义的单 Key compare-and-refresh Lua
  修复，不使用 `SET NX` 更新已存在的旧 Key。用户/角色权限的认证关键数据回源不得读延迟副本或 ORM 查询缓存。
- 同一 Key 的并发回源使用 JVM 本地的分段锁/单飞加载，不使用分布式锁；多实例下每个实例最多各加载一次。
- 用户 ID 来自可信会话，角色 ID 来自数据库，攻击者无法构造不存在的 Key，因此不做缓存穿透防护，也不缓存空值。
- 会话有效但用户在数据库中不存在：视为无效，撤销该会话并按 401 处理，不缓存。
- 角色在数据库中不存在（并发删除窗口）：按空权限处理，不缓存。
- 业务缓存读取/回填失败时，在会话 Lua 已成功且请求仍可安全判定的前提下回源主库；回填写失败只记录警告，不影响本次请求。数据库也失败：503。若会话相关
  Redis Lua 本身失败，则按 503 fail closed，不得转为匿名或放行。

### 9.4 缓存失效与事务后撤销

业务 Service 在事务内发布领域事件，`AuthCacheInvalidator` 使用
`@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = false)` 执行缓存删除（用户级会话撤销由事务内递增
`auth_version` 保证，5.5）。发布事件必须通过统一入口；入口同时检查
`TransactionSynchronizationManager.isActualTransactionActive()` 与 `isSynchronizationActive()`，不满足时抛异常，不使用
`fallbackExecution = true`
绕过事务语义。确实没有事务的场景，由应用服务在数据库操作成功后显式调用同步失效方法，并记录原因。每个触发点（用户禁用、删除、改密、重置密码、角色权限变更、角色删除、权限变更）都必须有集成测试，断言缓存失效确实发生；会引起会话撤销的触发点还必须断言
`auth_version` 在同一事务内递增（15.2、15.4）。

| 变更                        | 动作                                                                                                              |
|---------------------------|-----------------------------------------------------------------------------------------------------------------|
| 用户基本信息、用户启用、用户角色关系变更      | 删除 `user:{uid}`（通常不递增 `auth_version`）；若将 `must_change_password` 从 false 改为 true 以强制改密，必须在同一事务内递增 `auth_version` |
| 用户禁用、删除                   | 事务内 `bumpAuthVersion`；提交后删除 `user:{uid}`                                                                        |
| 修改本人密码、重置密码               | 事务内 `bumpAuthVersion`；提交后删除 `user:{uid}`                                                                        |
| 角色启用/禁用、编辑（含角色码）、角色权限关联变更 | 删除 `role-permissions:{roleId}`                                                                                  |
| 角色删除                      | 事务内先查出受影响用户；提交后删除角色缓存及这些用户的 `user:{uid}`                                                                        |
| 权限禁用、删除、编码变更              | 查出关联角色，删除这些角色的 `role-permissions`                                                                               |

缓存删除失败：短间隔重试 3 次，仍失败则记录 ERROR 日志（不包含 Token 或敏感信息）、增加对应失败指标并触发告警。事务提交后的
Spring 事件是进程内机制，无法覆盖“提交后、监听器执行前进程崩溃”；本期接受该边界，不得宣称可靠投递。用户级撤销依赖事务内递增的版本和有界用户缓存
TTL，不依赖事件保证立即失效。并发旧回源可能在删除后回填，但截止时间从回源开始时确定，最多约 66s 后到期（5.5、9.1）。

### 9.5 一致性说明

删除缓存的方案仍有两个边界：提交后进程崩溃导致删除未执行，以及并发旧回源在删除之后尝试回填。通过“独立主库短读事务 +
查询前确定截止时间 + 回填 `SET NX PXAT`”避免旧数据重新获得完整 TTL。普通后台接受用户状态最长约 66s、角色权限最长约 5.5m
的缓存窗口；缓存删除成功且没有旧值回填时通常立即生效。若要求权限变更严格实时生效，需要另行设计，不属于本期；8.6
的越权校验读库，不受权限缓存窗口影响。

## 10. API

具体路径遵循项目现有 API 命名规范，以下为建议：

| 方法   | 路径                      | 权限  | 说明                                                            |
|------|-------------------------|-----|---------------------------------------------------------------|
| POST | `/api/auth/login`       | 公开  | 用户登录                                                          |
| POST | `/api/auth/logout`      | 已认证 | 撤销当前会话，幂等；不触发续期                                               |
| POST | `/api/auth/touch`       | 已认证 | 用户操作触发的保活，成功返回 204，响应头带 `X-Session-Expires-In`；会触发续期          |
| POST | `/api/auth/password`    | 已认证 | 修改本人密码，成功返回 204；事务内递增 `auth_version`；旧会话在请求读到新版本后失效，保证边界见 5.5 |
| GET  | `/api/auth/me`          | 已认证 | 当前用户基础信息、`mustChangePassword` 和前端所需权限信息                       |
| GET  | `/api/auth/permissions` | 已认证 | 当前用户有效权限标识集合（如需单独获取）；强制改密状态下不可用                               |

登录请求：

```json
{
  "username": "admin",
  "password": "password-from-client"
}
```

登录成功响应（字段对齐项目统一响应体，响应头含 `Cache-Control: no-store`）：

```json
{
  "status": "SUCCESS",
  "accessToken": "opaque-random-token",
  "tokenType": "Bearer",
  "expiresIn": 1800,
  "absoluteExpiresAt": 1790043200,
  "mustChangePassword": false,
  "lastLoginAt": 1789990000,
  "lastLoginIp": "203.0.113.8"
}
```

- `status`：本期恒为 `SUCCESS`，预留给 MFA 等后续扩展。
- `expiresIn`：空闲超时秒数（每次有效活动后顺延）。
- `absoluteExpiresAt`：会话绝对过期时间（epoch 秒），到点必须重新登录。
- `lastLoginAt` / `lastLoginIp`：本次登录之前的最近一次登录信息，首次登录时为 null。
- 响应头 `X-Session-Expires-In`：任何续期成功的已认证响应都会带上，值为续期后会话的剩余空闲秒数；被排除续期的请求不带该头。前端据此判断服务端是否真的续期（12.2）。

不要返回密码哈希、Redis 会话内部信息或其他用户的敏感字段。`/me` 返回的权限列表仅用于前端展示和交互，真正的授权始终在后端执行。

## 11. HTTP 状态与统一错误处理

| 状态  | 场景                                                                                                                      |
|-----|-------------------------------------------------------------------------------------------------------------------------|
| 400 | 参数格式错误或校验失败；修改密码时旧密码错误（`AUTH_OLD_PASSWORD_INCORRECT`）                                                                   |
| 401 | 未登录、Token 缺失/无效、会话不存在/过期/被撤销、账号或密码错误、账号已禁用（登录接口）；带 `WWW-Authenticate: Bearer`                                           |
| 403 | 已认证但缺少所需功能权限；强制改密状态下访问受限接口（`AUTH_PASSWORD_CHANGE_REQUIRED`）；越权操作（`AUTH_PRIVILEGE_ESCALATION`、`AUTH_PROTECTED_RESOURCE`） |
| 429 | 登录尝试过多，带 `Retry-After`                                                                                                  |
| 503 | 会话/认证基础设施不可用（Redis 或数据库故障、Redis 写满）                                                                                     |
| 500 | 未预期的服务端错误                                                                                                               |

统一响应体沿用项目规范。错误响应不得泄露堆栈、Token、内部 Redis Key 或敏感 SQL。**前端只在 401 时清理登录状态，403 与 503
都不清理**（12.2）。

## 12. 前端约定

### 12.1 参数

| 参数                        | 默认值 | 约束                                                                                                                     |
|---------------------------|-----|------------------------------------------------------------------------------------------------------------------------|
| 服务端 `idle-timeout`        | 30m | Redis 会话滑动过期                                                                                                           |
| `touch-interval`          | 5m  | 用户发生真实交互时，若达到间隔则尝试 touch                                                                                               |
| `frontend-idle-timeout`   | 20m | 必须满足 `frontend-idle-timeout ≤ idle-timeout − touch-interval − 1m`（默认 5m − 5m − 1m = 24m，取 20m），给 touch 间隔、网络失败和时钟偏差留余量 |
| `frontend-warning-before` | 2m  | 小于 `frontend-idle-timeout`                                                                                             |

### 12.2 规则

1. 监听键盘、指针、触摸等真实用户交互，仅更新本地 `lastActiveAt`（写入 `localStorage`，所有标签页共享，写入需节流）；不在每次事件上发送网络请求。
2. **有效保活时间定义：**`lastRenewableServerContactAt` 只在收到**最终状态为 2xx 且带有 `X-Session-Expires-In` 响应头**
   时更新，包括成功的 `/api/auth/touch`；登录成功时也初始化为当前时间。响应头表示认证阶段确已刷新 TTL，但不保证业务处理成功；Filter
   先运行，因此 403 或 5xx
   也可能带该头，前端必须忽略这些响应。前端不得自行维护「哪些请求可续期」的清单，也不得把其他成功请求算作保活。401/403/503、没有该响应头的请求（含被服务端排除续期的后台轮询、健康检查）、静态资源请求均不更新该时间。前端可以用响应头的值（剩余空闲秒数）校准本地空闲倒计时。该时间写入
   `localStorage`，所有标签页共享。
3. 保活：发生真实用户交互时，若距 `lastRenewableServerContactAt` ≥ `touch-interval`，调用 `POST /api/auth/touch`。多标签页通过共享的
   `lastTouchAt` 或等价协调方式减少重复请求；协调机制只是优化，不能影响服务端安全性。后台轮询、定时器、页面可见性变化都不触发
   touch。
4. 前端触达不等于无条件续期：`/api/auth/touch` 仍须通过服务端会话有效性、用户状态、绝对过期与强制改密规则。
5. 空闲达到 `frontend-idle-timeout` 前 `frontend-warning-before` 提示用户；到达后尽力调用 logout，清理本地 Token
   与用户状态，跳转登录页。即使 logout 因网络失败，前端也必须清理本地状态；服务端会话最终由 TTL 到期。
6. 状态码处理：401 统一清理本地认证状态并跳转登录页；403 提示无权限，不当作登录过期，但当业务码为
   `AUTH_PASSWORD_CHANGE_REQUIRED` 时跳转改密页；503 提示稍后重试，不清理登录状态。
7. 页面恢复可见或网络恢复后，立即用本地时间判断是否已超过空闲阈值，超过则登出；否则正常继续，服务端 401
   兜底。前端计时器只负责体验，不能替代服务端校验。
8. 多标签页共享登录状态，通过 `storage` 事件同步登出和 Token 变化。
9. Token 存储（D5）：浏览器使用 `localStorage` 的前提是配置严格的 CSP、禁止渲染未经净化的 HTML（如 `v-html`、
   `dangerouslySetInnerHTML` 需审查）、定期审计第三方依赖。CSP 与 HTML 净化不能阻止所有 XSS 读取 localStorage
   Token；该方案接受此风险。若威胁模型要求更高，应另行设计 HttpOnly + Secure + SameSite Cookie，并重新评估 CSRF。
10. App、小程序：Token 优先存放在系统安全存储（Keychain/Keystore）或平台提供的安全存储中；本期服务端空闲配置对所有端相同，若需区分，后续按
    `clientType` 配置。
11. 登录响应的 `status` 不是 `SUCCESS` 时视为登录未完成，不保存 Token。
12. `mustChangePassword = true` 时登录后直接进入改密页，只允许改密和退出；改密成功后清理本地状态并要求重新登录。
13. 登录成功后展示「上次登录时间与 IP」（首次登录不展示），提示用户核对是否为本人操作。

## 13. 安全要求

- 全站 HTTPS。
- 密码使用安全哈希存储，使用 Spring Security `PasswordEncoder`。如使用 BCrypt，编码前按 UTF-8 字节长度限制为不超过 72
  字节，所有写密码入口一致拒绝超长输入，不允许静默截断。
- Token 由 `SecureRandom` 生成，Redis 仅存摘要；不写入日志，不通过 URL 传输。网关、访问日志、APM 等必须屏蔽 `Authorization`
  请求头。
- 登录响应加 `Cache-Control: no-store`，避免 Token 被中间层缓存。
- 登录限流（6.2）；用户禁用、角色权限修改、退出登录、会话撤销、越权拦截等关键操作记录审计（6.3）。
- 权限校验默认拒绝（8.4）；管理操作越权防护（8.6）。
- 所有用户输入必须校验；客户端传来的角色、权限、用户 ID 不作为授权依据。
- Redis 部署满足 5.7 的全部要求。
- 认证相关 Key 的前缀与 TTL 统一由代码常量/配置管理，不允许出现无 TTL 的认证类 Key。

## 14. 数据模型与初始化

### 14.1 数据模型

优先复用项目现有表和实体；缺失时按下表补齐（命名遵循项目规范）：

| 实体    | 关键字段与约束                                                                                                                                                                                 |
|-------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 用户    | `id`、`username`（唯一）、`password_hash`、`display_name`、`status`、`must_change_password`（默认 false）、`auth_version`（BIGINT，非空，默认 0，只能通过 `bumpAuthVersion` 原子递增）、`last_login_at`、`last_login_ip` |
| 角色    | `id`、`role_code`（唯一）、`name`、`status`                                                                                                                                                    |
| 权限    | `id`、`permission_code`（唯一）、`name`、`type`、`status`                                                                                                                                       |
| 用户-角色 | `user_id`、`role_id`，联合唯一                                                                                                                                                                |
| 角色-权限 | `role_id`、`permission_id`，联合唯一                                                                                                                                                          |

约定：

- `role_code` 为服务端内部识别符，必须与 `auth.super-admin-role-code` 对齐；本项目沿用现有 `sys_role` 表，通过迁移
  `V3__role_code.sql` 增加 `role_code` 列与唯一索引，并将种子角色初始化为 `SUPER_ADMIN`/`USER`。
- 角色权限沿用现有 `sys_menu.permission` 权限码，不新建独立的权限表；角色有效性由关联菜单与角色状态决定。
- `role_code` **不下发到客户端**（理由见 8.6），角色出参模型（`RoleDTO`）不含该字段。

### 14.2 种子数据与初始化

- 权限码通过项目现有的迁移机制（Flyway/Liquibase/SQL 脚本）写入，不由运行时代码隐式创建。
- 初始管理员通过一次性初始化创建（迁移脚本或 `ApplicationRunner`），初始密码来自环境变量或配置，不在仓库中提交默认口令；环境变量缺失时不创建。初始管理员的
  `must_change_password` 置为 true，`auth_version` 为默认值 0。若项目已有初始化机制则复用。
- 超管角色按 `super-admin-role-code` 创建，并关联初始管理员。

## 15. 测试与验收标准

### 15.1 登录与限流

- 正确账号密码可登录并获得 Token，响应包含 `status`、`expiresIn`、`absoluteExpiresAt`、`mustChangePassword`，响应头包含
  `Cache-Control: no-store`。
- 第二次登录返回上一次的 `lastLoginAt` 与 `lastLoginIp`；首次登录这两项为 null。
- 错误密码、不存在的用户返回相同的错误响应。
- 已禁用用户：密码正确时返回「账号已禁用」；密码错误时返回通用错误。
- 账号或 IP 登录尝试额度耗尽后返回 429，且不进行密码校验；尝试额度统计所有登录请求，而不是只统计错误密码。
- 登录尝试限流与首次设置 TTL 在同一 Lua 脚本内完成；并发情况下被准入的请求数不得超过账号/IP 上限，固定窗口内后续请求不得重置
  TTL。
- 成功建会话频率限制：超过 `max-successes-per-account` 返回 429 且不创建会话；额度判定和占用原子完成，计数 Key 带
  TTL；窗口过后可再次登录。
- 以真实 Redis 对账号维度和 IP 维度分别执行并发限流测试，校验不存在“并发请求先全部通过检查、之后才递增”的窗口。
- Redis 中不出现原始 Token；审计与日志中不含密码和原始 Token。

### 15.2 会话、续期与改密

- **所有 Lua 脚本与并发测试必须在真实 Redis 上运行**（Testcontainers 或项目已有的测试 Redis），不得使用 mock 的
  `StringRedisTemplate` 或内存模拟；CI 无法提供 Redis 时，在汇总中明确列为未验证。
- 缺少 Token 访问受保护接口返回 401；无效、过期、撤销的 Token 返回 401。
- 退出登录后原 Token 不能继续访问。
- 并发测试：续期与登出并发时，登出完成后会话 Key 不存在；登出先发生时续期不得重建会话。
- 每个允许续期的有效请求均通过 Lua 原子检查会话并刷新 TTL；不存在 `renew-min-interval` 配置或节流逻辑。
- 超过空闲 TTL 后返回 401；有效请求成功续期后 TTL 更新为 `min(idle-timeout, absoluteExpireAt - now)`，不因续期节流提前过期。
- 超过最大存活时长后，即使持续请求也返回 401；任何时刻续期后的 TTL ≤ 距绝对过期剩余时间。
- 命中 `renew-exclude-paths` 的请求不续期；`/api/auth/touch` 续期。
- 认证阶段续期成功时设置 `X-Session-Expires-In`；被排除续期的请求与未建立会话的 401 不带该头。由于 Filter 先于下游
  Controller/方法鉴权执行，后续 403 或 5xx 可能仍带该头；前端必须只在最终 2xx 且存在该头时更新保活时间。跨域请求下前端可以读取该头。
- Filter 每个请求只调用一次会话 Lua 脚本（不存在独立的 `readSession` 加二次校验）。
- 用户禁用/删除、改密、重置密码后：事务内 `auth_version` 递增；缓存删除成功且无并发旧值回填时，后续请求立即使用新数据。模拟缓存删除失败、进程崩溃和并发旧值回填，旧用户缓存必须在原始
  DB 读取开始时间 + 随机 TTL 前到期，且从事务提交时算起最迟约 66s 后，新请求必须回源主库并拒绝旧会话。已在事务提交前开始的在途请求允许完成，文档不得声称它们会被中断。
- Redis 会话不可用时返回 503，不放行。
- 带无效 Token 访问公开接口不受影响。
- `must_change_password = true` 的会话：访问 `PASSWORD_CHANGE_ALLOWED_PATHS` 之外的任何接口（包括仅标注`isAuthenticated()`
  的接口）都返回 403 且业务码为 `AUTH_PASSWORD_CHANGE_REQUIRED`；改密成功后旧会话失效，重新登录后恢复正常。
- 修改密码：`password-limit` 超限时返回 429 且不校验旧密码；并发改密尝试不能超过准入额度；密码额度 Key 带固定窗口
  TTL；旧密码错误返回 400 并产生失败审计/指标；新密码违反策略被拒绝；事务内递增 `auth_version`
  ；旧会话（含当前会话）在请求读到新版本后失效，同时覆盖缓存删除失败与并发旧值回填边界。
- 版本拒绝：用户 `auth_version` 递增后，`authVersion` 小于它的会话请求返回 401，会话 Key 被删除，
  `auth_session_rejected_total{reason=version_stale}` 递增；递增之后重新登录的会话（`authVersion` 等于当前版本）不受影响。
- 用户缓存版本小于会话版本时，不可直接放行或直接按版本落后拒绝；必须回源主库校验并按主库值判定，不能使用旧缓存的 `status` /
  `mustChangePassword`；随后必须原子修复旧缓存，避免后续每请求重复回源。若主库版本低于会话版本则 fail closed 并告警。
- 登录与撤销并发：先读取旧用户快照（含 `auth_version`），再让用户被禁用或改密，最后创建会话；新会话的请求不得使用`authVersion`
  低于会话版本的缓存绕过 `mustChangePassword`；若缓存版本低于会话版本必须回源主库，版本或用户状态不满足时返回 401。
- 禁用后再启用：启用不递增版本，但禁用时已递增，禁用前建立的旧会话不会复活。
- 模拟 Redis 主从切换丢写：手动重建一个因用户级撤销而版本落后的会话 Key，访问返回 401。单独登出后若 `DEL` 丢失，允许该会话在剩余
  TTL 内复活；该已知边界需在文档和测试中明确，不能错误地声称 `auth_version` 会保护普通登出。
- 被禁用用户或版本落后的会话在同一次请求中可能被续期一次，随后即被删除，下一次请求返回 401。
- 启用用户、用户角色变更、角色权限变更不递增 `auth_version`，已登录会话不受影响（权限按 D13 通过缓存失效更新）。
- `bumpAuthVersion` 在没有活动事务时抛出异常。
- 每个触发点（用户禁用、删除、改密、重置密码、将 `must_change_password` 从 false 改为 true）都有集成测试，断言 `auth_version`
  在同一事务内递增，且业务事务回滚时版本不变。

### 15.3 功能权限

- 具备对应权限的用户可调用接口；已登录但缺少权限返回 403（经由统一异常处理仍保持 403）。
- 多角色用户获得合并去重后的权限；禁用角色、禁用权限不生效。
- 拥有有效超管角色的用户可访问所有受 `perm.has` 保护的接口；通过 Service/API 尝试禁用、删除或修改超管角色必须按 R4
  被拒绝。可在隔离的测试夹具中直接调整测试数据并执行缓存失效，验证角色缓存刷新后确实不再提供超管权限；这不是允许业务接口修改超管角色的理由。
- 伪造权限列表、角色 ID 或管理员标志不能获得额外权限。
- 默认拒绝测试（8.4）通过：所有接口均有授权声明或属于公开端点。
- 权限码一致性测试（8.4）通过。
- 无 Token 的 OPTIONS 预检返回正常 CORS 头；`/error` 可访问。

### 15.4 缓存一致性

- 用户角色关系变更后，用户缓存被清理并重新从数据库加载。
- 角色启用/禁用、角色权限变更、权限禁用后，对应角色缓存被清理。
- 角色删除后，受影响用户缓存被清理。
- 缓存未命中能正确回源；并发回源只触发一次数据库加载（单进程内）。
- 缓存失效失败可观测（日志/指标），并有重试；将 `must_change_password` 由 false 改为 true 时，认证版本与业务标记在同一事务内更新。
- 每个事务后触发点（用户禁用、删除、改密、重置密码、角色权限变更、角色删除、权限变更）都有集成测试，断言缓存失效确实发生；会引起会话撤销的触发点另断言
  `auth_version` 同事务递增（15.2）。
- 在没有活动事务或事务同步未激活时发布失效事件或调用 `bumpAuthVersion`，立即抛出异常，而不是静默不执行。
- 并发请求下不会因缓存缺失或 Redis 异常而错误放行。

### 15.5 前端

- 前端达到空闲阈值后清理认证状态并跳转登录页；服务端仍独立校验。
- 用户持续操作但无业务请求时，通过 touch 保持会话，不被误登出。
- 后台轮询与健康检查不能延长会话；只有收到带 `X-Session-Expires-In` 响应头的成功响应才更新 `lastRenewableServerContactAt`
  ，没有该头的请求（含被排除续期的轮询）不更新；前端代码中没有自行维护的可续期请求清单。
- 401 清理登录状态，403 与 503 不清理；403 + `AUTH_PASSWORD_CHANGE_REQUIRED` 跳转改密页。
- 多标签页登出可同步。
- 登录成功后展示上次登录信息；未知 `status` 不保存 Token。

### 15.6 管理操作越权防护

接口已存在时，以下测试在接口层执行；接口不存在时，在 `AdminPrivilegeGuard` 单元层面执行，并在汇总中说明未覆盖的接口层。

- 仅拥有用户管理权限的非超管，无法给用户分配权限超出自身的角色（R1）；无法新增或移除自己没有的权限到角色上（R2）。
- 无法修改自己的角色关系，无法修改自己所拥有角色的权限关联（R3）。
- 任何人无法删除、禁用超管角色或编辑其权限关联；非超管无法授予/回收超管角色，无法操作超管账号（R4、R5）。
- 无法禁用或删除自己（R6）；无法禁用、删除最后一个启用的超管账号，也无法摘除其超管角色（R6）。
- 并发场景：两个管理员同时分别禁用两个超管，最终至少保留一个启用的超管。
- 越权判断以数据库为准：缓存中残留的旧权限不影响校验结果。
- 违规返回 403 及正确的业务码，并产生 `PRIVILEGE_DENIED` 审计与指标。
- 批量操作中任一项违规，整体回滚。

### 15.7 可观测性

- 登录成功、失败、被禁用、尝试限流/成功建会话限流、401/403/503 拒绝、续期、Redis
  异常、缓存失效失败、缓存版本落后回源、认证版本不一致、越权拦截、请求期会话拒绝、认证版本递增都有对应指标递增。
- 指标标签中不出现用户名、uid、IP。

## 16. Agent 实施顺序与约束

按以下顺序实施，每一步完成后运行相关测试。严格遵守 1.1–1.4 的范围划分。

1. **现状盘点**：检查模块结构、统一响应体、异常处理、数据库模型、Redis 客户端、密码编码器、CORS、审计、指标与日志脱敏；识别现有用户/角色管理接口和后台轮询接口。与第
   2 节决策冲突时停止并报告，不自行更改架构。
2. **数据与配置**：仅补齐本期必要的用户/角色/权限关系及字段（14.1）；复用已有迁移、种子数据、初始管理员机制。增加会话超时、缓存
   TTL（用户缓存 60s、角色权限缓存 5m）、登录尝试准入限流和成功建会话频率限制；用户表新增 `auth_version`；不得新增每请求查库模式或
   `renew-min-interval`。
3. **Token 与 SessionService**：实现 Token 生成/摘要；实现会话创建与原子认证续期的 Lua 脚本（均只访问单个 Key），以及单会话撤销（
   `DEL`）。为脚本结果、绝对过期、Redis 异常和并发竞态编写单元/集成测试，测试运行在真实 Redis 上。
4. **登录与限流**：实现或复用 `LoginRateLimiter`、`LoginService`、`AuthService`；确保固定窗口计数只在首次创建 Key
   时设置 TTL；实现成功登录频率限制；登录时用同一次数据库读取的快照获得 `status` 与 `auth_version` 并写入会话。
5. **API 与安全过滤器**：实现或复用登录、退出、touch、当前用户、修改本人密码 API；实现 `TokenAuthenticationFilter`（按 7.2
   的顺序，含 `auth_version` 版本校验、缓存版本落后时回源主库与 `X-Session-Expires-In` 响应头）、`SecurityConfig`（含 CORS
   暴露该响应头）、401/403 处理器和强制改密限制。
6. **RBAC 与缓存**：实现/复用用户缓存、角色权限缓存、`PermissionChecker`；为存量 Controller 接入 `@PreAuthorize`
   ；补默认拒绝测试与权限码一致性测试。
7. **认证版本与缓存失效**：实现 `AuthVersionService.bumpAuthVersion`，在用户禁用、删除、改密、重置密码及强制设置
   `must_change_password=true` 的业务事务内调用；接入这些触发点和角色权限变更后的缓存失效；发布事件使用带事务检查的统一入口，每个触发点配集成测试；重试失败须可观测，文档不得宣称跨系统强一致。
8. **管理操作越权防护**：实现或复用 `AdminPrivilegeGuard`，只接入项目已有的用户管理/角色管理业务；不存在的业务接口不新建。关键规则在
   Service 层读库校验并覆盖并发防自锁。
9. **审计与指标**：优先接入项目现有审计和 Micrometer 指标体系；若已有能力，不新建重复框架。确保敏感信息不进入日志与指标标签。
10. **前端保活**：实现真实交互检测、`lastRenewableServerContactAt`（仅在收到带 `X-Session-Expires-In` 响应头的成功响应时更新）、touch
    协调和空闲退出；前端不得维护可续期请求清单。
11. **验收测试**：运行第 15 节全部适用测试，特别是会话创建与续期的原子性、`auth_version` 撤销及其并发场景、前端保活判断、Redis
    故障返回 503、缓存失效失败边界。
12. **运维检查与交付汇总**：对 5.7 的 Redis 部署要求（含确认是单分片、非 Cluster
    部署）、HTTPS、时间同步、日志脱敏输出检查清单及状态；汇总新增/修改文件、配置、API、测试命令与结果、`renew-exclude-paths`
    的依据、尚未接入的 `AdminPrivilegeGuard` 业务点、未完成事项与未知权限码。另明确报告普通认证请求实测 Redis 往返数（目标约
    3 次）及是否发生了数据库查询。

### 实施约束

- 不引入 JWT 或 OAuth 2.0；不实现数据权限；不实现 MFA、step-up、顶号、踢人、会话列表或复杂多端登录策略。
- 本期不实现 `renew-min-interval` 或其他续期节流；续期校验与 TTL 刷新统一由 `SessionService` 的 Lua
  脚本原子完成，将来如需节流也只能在脚本内进行。
- 不得使用 Java 侧多条独立 Redis 命令模拟原子会话创建（`HSET` 与 `EXPIRE` 必须原子）；不得建立 `user-sessions`
  索引或实现按用户批量删除会话的逻辑，会话撤销只通过 `auth_version`。
- Lua 脚本的 `now` 由应用 `Clock` 经 `ARGV` 传入；每个脚本只访问单个 Key；不使用 Redis Cluster，不为适配 Cluster 改写脚本。
- 不得在没有活动事务或事务同步未激活的情况下发布缓存失效事件或调用 `bumpAuthVersion` 而不报错；`bumpAuthVersion`
  必须与触发它的业务变更在同一个数据库事务内。
- 用户级撤销依赖数据库 `auth_version`，普通登出依赖 Redis `DEL`；不得把两者的故障保证混为一谈，也不得把“事务后事件删除会话”当作撤销机制。
- 正常已认证请求不执行数据库查询；用户/角色缓存回源使用查询开始前确定的绝对截止时间和 Redis `SET NX PXAT`，不可把旧数据缓存窗口延长。
- 前端是否算作有效保活，以服务端 `X-Session-Expires-In` 响应头为准，不得自行维护可续期请求清单。
- 不为本功能重构无关模块；除添加授权注解和接入 `AdminPrivilegeGuard` 外，不修改无关 Controller 的业务逻辑。
- Redis 部署要求默认作为运维检查项，不假设 Agent 可以直接修改生产环境。
- 优先采用 Spring Boot 3 / Spring Security 6 现行 API；复用项目已有 Redis 客户端、统一响应规范、审计机制和指标体系。
- 保持类职责单一、命名直观，避免不必要的抽象层。
- 不得只生成方案而不修改代码（如果 Agent 的任务是实施）；完成后必须运行构建和测试并报告结果。

## 附录 A. 交付前自检

以下检查针对代码、配置和测试，不含本文档中对这些做法的禁令描述。交付前逐项确认：

- 代码与配置中不存在 `renew-min-interval`、`renewMinInterval`、续期节流实现，也不存在独立的 `readSession` 加二次校验流程。
- 代码中不存在 `user-sessions` 索引、`revokeAllByUser`、`SMEMBERS` 或任何按用户批量删除会话的实现；会话撤销只通过
  `auth_version`。
- 代码、配置与数据模型中没有 `password_changed_at` 字段或相关校验（已由 `auth_version` 取代）。
- 会话 Lua 脚本的 `now` 都来自 `ARGV`，每个 Lua 脚本只访问单个 Key；缓存写入用单 Key `SET NX PXAT`，要求 Redis 6.2+
  且所有应用节点和 Redis 节点进行 NTP 时间同步。当前部署不使用 Redis Cluster，也没有为适配 Cluster 改写脚本。
- 文档、日志与交付汇总中，没有把事务提交后的进程内事件描述为可靠投递，也没有声称 Redis 或应用故障时所有旧会话仍保证立即失效；明确普通登出
  `DEL` 丢失时会话可能复活。
- 事件发布与 `bumpAuthVersion` 统一经过带事务检查的入口；每个触发点都有集成测试，且 `auth_version`
  与业务变更同事务提交或回滚；缓存回填截止时间以数据库查询开始时间为基准，不能被回填动作延长。
- 前端代码中没有自行维护「可续期请求」清单的逻辑；`lastRenewableServerContactAt` 只在最终 2xx 且有 `X-Session-Expires-In`
  时更新，且该响应头已在 CORS 中暴露。
- Redis 部署检查结果中包含「单分片、非 Cluster」的确认状态。
- 普通已认证请求目标约 3 次 Redis 往返且不访问数据库；不存在每请求查库模式。
- 用户与角色权限缓存回填的截止时间在独立短读事务启动前确定；回源读取使用主库/权威读和当前已提交数据，不复用过期事务快照。普通
  miss 使用 `SET NX PXAT`，用户缓存版本落后于会话时使用单 Key compare-and-refresh Lua；两者均不能覆盖并发写入的较新缓存，且并发旧值回填的
  TTL 上限测试通过。
- Lua 脚本与并发测试在真实 Redis 上通过；无法运行的，在汇总中列为未验证。
- 没有把 Redis 生产部署修改、MFA、数据权限或项目中不存在的管理业务接口列为本期必须编码实现的内容。
