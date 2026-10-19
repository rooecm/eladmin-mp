# 后端认证与安全整体链路

按当前代码整理。认证采用不透明 Bearer Token，HTTP 请求不使用服务端 HttpSession；Redis 保存会话，数据库保存用户、角色、菜单及认证版本。

## 1. 整体链路

```mermaid
flowchart TD
    A[HTTP 请求] --> B[SpringSecurityConfig / SecurityFilterChain]
    B --> C{请求类型}
    C -->|登录| D[AuthController.login]
    C -->|其他公开路径| PUB[放行到对应处理器]
    C -->|受保护请求| H[TokenAuthenticationFilter]

    D --> E[AuthServiceImpl.verifyCredentials]
    E --> F[数据库快照 → BCrypt → 账号状态检查]
    F --> G[establishSession：生成 Token → Redis 创建会话]
    G --> LOGIN[返回 Token、有效期和强制改密标记]

    H --> I[RedisSessionService：Lua 会话检查及按需续期]
    I --> J[用户缓存或主库回源 → 状态与 authVersion 校验]
    J --> K[角色权限缓存或主库回源 → 创建 SecurityContext]
    K --> L[强制改密路径限制]
    L --> M[方法授权：PreAuthorize / PermissionChecker]
    M --> N[业务 Controller → Service]
    N --> O{操作类型}
    O -->|普通业务| P[Mapper → 数据库]
    O -->|安全写入| Q[锁超管行 → 数据库权限守卫 → 事务写入]
    Q --> R[用户级撤销操作：同事务递增 authVersion]
    Q -->|普通权限变更| S[按需发布缓存失效事件]
    R --> S
    S --> T[事务提交后删除相关缓存]
    T --> U[后续请求读取新身份与权限，或拒绝旧会话]
```

图中展开成功主链；认证失败、基础设施故障及事务回滚分支见后面的细图。普通业务写入是否需要权限守卫，取决于是否修改用户、角色、菜单等安全状态；新建对象不存在旧缓存时不需要执行对应的失效操作。

## 2. 分层及代码职责

| 所在模块 / 包                         | 主要类                                                                                                                                          | 职责                                                                 |
|----------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------|
| admin-bootstrap                  | 启动类、application 配置、Flyway V4                                                                                                                 | 组装应用、加载安全参数和数据库迁移                                                  |
| admin-common                     | AuthErrorCode、ApiResult                                                                                                                      | 错误码和统一响应模型                                                         |
| admin-framework / framework      | SpringSecurityConfig、TokenAuthenticationFilter、PermissionChecker、TokenService                                                                | 安全过滤链、请求身份、方法授权、令牌生成与摘要；定义 SessionService 和 AuthIdentityService 接口 |
| admin-framework / infrastructure | RedisSessionService、RedisAuthCache、Lua                                                                                                       | 实现 Redis 会话及缓存                                                     |
| admin-system / modules.security  | AuthController、AuthServiceImpl、AuthIdentityServiceImpl、用户/角色缓存服务、AuthReadService、AdminPrivilegeGuard、AuthVersionService、AuthCacheInvalidator | 登录及改密业务、身份组装、数据库权威读取、安全写入守卫与提交后失效                                  |
| admin-system / modules.system    | 用户/角色/菜单 Controller、Service、Mapper                                                                                                           | 承载原有业务；在安全写事务中调用守卫、版本服务和失效服务                                       |

框架过滤器通过 AuthIdentityService 接口获取身份；AuthIdentityServiceImpl 在系统模块中实现该接口。权限写入守卫直接使用当前事务里的数据库数据，缓存回源使用
AuthReadService 的独立短事务。

## 3. 登录与建立会话

```mermaid
sequenceDiagram
    autonumber
    participant C as 客户端
    participant A as AuthController
    participant S as AuthServiceImpl
    participant P as ProviderManager / DaoAuthenticationProvider
    participant U as LoginService
    participant Q as AuthReadService / AuthMapper
    participant DB as 主数据库
    participant R as TokenService / RedisSessionService
    participant RS as Redis 会话存储

    C->>A: POST /api/auth/login
    Note over A: 公开接口；参数校验，响应禁止缓存
    A->>S: verifyCredentials(request)
    S->>S: 校验密码长度、规范化用户名
    S->>P: authenticate(username, password)
    P->>U: loadUserByUsername
    U->>Q: login(canonicalUsername)
    Q->>DB: 查询用户及密码哈希
    Note over Q,DB: REQUIRES_NEW + READ_COMMITTED，只读主库
    DB-->>Q: UserDO
    Q-->>U: 同次数据库快照
    U-->>P: LoginCredentialSnapshot
    P->>P: BCrypt 校验；成功后擦除凭证
    P-->>S: 已验证的同次用户快照
    S->>S: 正确密码之后检查 enabled
    S-->>A: verified
    A->>S: establishSession(verified, clientType, ip, agent)
    S->>R: 生成随机 Token，计算 SHA-256 摘要
    R->>RS: Lua 原子创建 Redis Hash 和 TTL
    RS-->>R: 创建结果
    R-->>S: sessionId、绝对到期时间、TTL
    S->>DB: 尝试记录本次登录时间和 IP
    Note over S,DB: 记录登录信息失败只记日志，不中断登录
    S-->>A: LoginResponse
    A-->>C: LoginResponse 经 Advice 包装为 ApiResult，含 Token、有效期、mustChangePassword
    Note over S: establishSession 的 finally 再次擦除凭证
```

Token 使用 32 字节安全随机数，经 Base64URL 编码为 43 个字符；Redis 会话键为 `rooe:auth:session:<SHA-256摘要>`。会话记录保存创建时的
authVersion，不包含密码。

默认空闲期限为30分钟、绝对期限为12小时；续期只能延长空闲期限，不能越过绝对期限。登录和修改密码不做次数限流；登录记录的 IP 取
remoteAddr。

## 4. 每次请求的认证与授权

```mermaid
flowchart TD
    A[受保护请求携带 Authorization: Bearer Token] --> B[TokenAuthenticationFilter：清理旧上下文]
    B --> C{Token 格式有效？}
    C -->|否| ANON[匿名进入下游过滤链]
    ANON --> E401[Spring Security 拒绝：401]
    C -->|是| D[计算摘要，确定本路径能否续期]
    D --> E[RedisSessionService.authenticateAndRenew]
    E --> F{Lua 返回有效会话？}
    F -->|不存在或过期| E401
    F -->|有效| G[AuthIdentityServiceImpl → UserCacheServiceImpl]
    G --> CACHE[GET 用户缓存]
    CACHE --> HIT{可用且版本不落后于会话？}
    HIT -->|否| LOAD[分段锁后复查 → AuthReadService 读主库 → 有界回填]
    HIT -->|是| CHECK
    LOAD --> CHECK{用户存在、启用且版本等于会话？}
    CHECK -->|否| DEL[删除该摘要会话、记录拒绝原因]
    DEL --> E401
    CHECK -->|是| HEADER[本次已续期时设置 X-Session-Expires-In]
    HEADER --> FORCE{mustChangePassword？}
    FORCE -->|否| ROLE[RolePermissionServiceImpl：MGET 角色权限缓存]
    ROLE --> RHIT{所需角色全部命中？}
    RHIT -->|否| RLOAD[加锁复查并从主库加载缺失角色]
    RHIT -->|是| VALUES[汇总启用角色的权限与角色码]
    RLOAD --> VALUES
    FORCE -->|是| EMPTY[业务 authorities 为空]
    VALUES --> CTX[LoginUser → Authentication → SecurityContext]
    EMPTY --> CTX
    CTX --> LIMIT{强制改密且访问非允许路径？}
    LIMIT -->|是| E403[403 PASSWORD_CHANGE_REQUIRED]
    LIMIT -->|否| AUTH[过滤链要求已认证，方法注解执行授权]
    AUTH --> PERM[业务权限通过 perm.has 检查；仅需登录的接口依赖全局认证]
    PERM --> RESULT{授权通过？}
    RESULT -->|否| DENY[403 ACCESS_DENIED]
    RESULT -->|是| BIZ[执行 Controller → Service → Mapper]
    BIZ --> RESP[返回结果；过滤器 finally 清理上下文]
    E -. 会话 Redis 异常 .-> E503[503 AUTH_UNAVAILABLE]
```

角色权限缓存命中时直接组装 authorities；回源只发生在缺失、过期、损坏或缓存不可用等情况。用户缓存若落后于当前会话版本，会先回源修复；若最终用户快照与会话版本不相等，则拒绝会话。

强制改密只允许 `/api/auth/me`、`/api/auth/password`、`/api/auth/logout`、`/api/auth/touch`。权限检查支持启用的 SUPER_ADMIN
角色；不依据用户 isAdmin 标志放行。

认证准备阶段的运行时异常由过滤器转换为503；下游业务异常由业务异常处理链处理。过滤器执行的各分支最终都会清理上下文。公开路径由
shouldNotFilter 跳过；OPTIONS 由安全链允许，跨域预检由 CORS 处理。

普通非强制改密请求在用户、角色缓存命中且 Redis 脚本预热时，认证部分为：一次会话 Lua、一次用户 GET、一次角色 MGET，共3次 Redis
操作、0次数据库读取。该预算不包含业务处理器自己的查询。

缓存回源通过 `AuthReadService` 的独立 READ_COMMITTED 事务读取主库，回填截止时间在查询前计算；NX/PXAT 与
compare-and-refresh 避免覆盖并发新值、延长旧快照寿命。用户缓存默认60秒、角色权限默认5分钟，均有约±10%抖动。缓存读写故障会尝试数据库回源；会话存储故障直接拒绝请求。

## 5. 安全写入、权限更新与会话失效

```mermaid
sequenceDiagram
    autonumber
    participant C as 已通过方法授权的请求
    participant S as 用户 / 角色 / 菜单 Service 或 AuthServiceImpl
    participant G as AdminPrivilegeGuard
    participant DB as 主数据库
    participant V as AuthVersionService
    participant I as AuthCacheInvalidator
    participant R as RedisAuthCache
    participant F as 后续请求的 TokenAuthenticationFilter

    C->>S: 修改安全状态
    Note over S,DB: 开启原业务事务
    S->>G: lockAdministration()
    G->>DB: 锁定 SUPER_ADMIN 角色公共行 FOR UPDATE
    S->>G: 按操作执行 R1–R6 权限检查
    G->>DB: 在当前事务查询真实角色、权限和超管数量
    G-->>S: 通过；不通过抛出403并回滚
    Note over S,DB: 本人改密还会锁用户行并校验旧密码及新密码策略
    S->>DB: 更新用户、密码、角色、菜单或关系表
    opt 用户级撤销：禁用、删除、改密、重置、初始化强制改密
        S->>V: bumpAuthVersion(userId, reason)
        V->>DB: 在同一事务中 auth_version + 1
        Note over S,DB: 删除用户时，版本递增发生在删除行之前
    end
    S->>I: 按需发布 Invalidation 事件
    alt 事务成功提交
        DB-->>S: COMMIT，释放锁
        I->>R: 对已发布事件执行 AFTER_COMMIT，删除最多尝试3次
        F->>R: 后续请求读取用户 / 角色缓存
        Note over F,DB: 缺失时从主库读取新快照
        alt 用户缺失、禁用或版本不一致
            F->>F: 删除当前摘要会话，返回401
        else 普通角色 / 菜单权限变更
            F->>F: 按刷新后的 authorities 执行方法授权
        end
    else 事务回滚
        DB-->>S: ROLLBACK，状态与版本一并回滚
        Note over I,R: 不执行 AFTER_COMMIT 缓存删除
    end
```

R1–R6
的职责分别为：限制授予用户的权限、限制角色/菜单权限扩张、限制修改自身角色关系及所持角色的权限/状态、保护超管角色、保护超管账户、保护自身禁用/删除及最后一个启用超管。具体条件以各守卫方法为准，超管也需要遵守适用于自己的保护规则。

普通角色关系、角色权限和菜单权限变化通过提交后缓存失效更新 authorities，不统一递增用户
authVersion。用户禁用、删除、改密、重置密码及初始化强制改密才走用户级版本撤销链路。

失效事件在进程内处理；进程崩溃或删除重试失败时仍依赖缓存 TTL 收敛，不能把数据库提交理解为所有已有会话立即收到撤销通知。初始化器
InitialAdminInitializer 在配置初始密码且满足历史演示账户条件时，执行单独的启动事务，锁角色/用户并更新密码、强制改密标记和版本，再提交后失效缓存。

## 6. 认证端点及错误返回

| 端点                      | 过滤器之后的主要业务                                      |
|-------------------------|-------------------------------------------------|
| POST /api/auth/login    | 凭证验证、随机令牌、创建 Redis 会话                           |
| GET /api/auth/me        | 从 principal 读取身份，查询用户展示数据及有效权限，返回强制改密标记         |
| POST /api/auth/touch    | 续期发生在过滤器中；Controller 无额外写入，返回204                |
| POST /api/auth/logout   | 此路径不续期；AuthServiceImpl 删除当前摘要对应的 Redis 会话，返回204 |
| POST /api/auth/password | 限流、锁与守卫、密码校验、事务内改密及版本递增、提交后失效，成功204             |

普通退出只删除当前会话，没有用户会话索引、会话列表、批量踢人链路。用户级版本撤销由后续请求逐个识别旧会话。

| 场景                   | 返回链路                            |
|----------------------|---------------------------------|
| 未认证、会话失效、凭证错误、账号禁用   | 401；包含 WWW-Authenticate: Bearer |
| 方法权限不足、守卫拒绝、强制改密路径限制 | 403                             |
| 会话基础设施不可用、无法完成身份确认   | 503                             |
| 参数或密码策略不满足           | 400                             |

过滤链失败由 SecurityErrorResponseWriter / Security 的入口与拒绝处理器输出；进入 MVC 后的异常由 GlobalExceptionAdvice
转换为统一 ApiResult。AuthTelemetry 在登录、续期、拒绝、版本变化及失效失败等位置记录指标和审计。
