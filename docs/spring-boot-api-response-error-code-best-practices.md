# Spring Boot 企业管理系统统一响应与错误码设计规范

> 项目当前实现和完整错误码清单见 [项目错误码使用说明](error-codes.md)。当前响应固定为 `code/message/data`，HTTP 仅使用
> 200、400、401、403、404、409、500，由异常类型和处理场景直接设置，无需登记业务错误码的 HTTP 映射。本文件其余字段为通用扩展建议。

> 适用技术栈：Java 17、Spring Boot、Spring MVC、Spring Security\
> 设计目标：统一 API 响应格式、遵循 HTTP
> 语义、规范业务错误码，并便于前后端协作和生产排障。

## 1. 核心结论

推荐同时使用 **HTTP 状态码 + 业务错误码 + 统一响应对象**：

-   **HTTP 状态码**：表达 HTTP
    层面的处理结果，例如认证失败、权限不足、资源冲突、服务器异常。
-   **业务错误码**：表达具体的应用或业务失败原因，例如用户名重复、账号禁用、订单状态不允许变更。
-   **统一响应对象 `ApiResult<T>`**：同时承载成功和错误响应结构，错误时
    可包含 HTTP 状态码、业务错误码和时间戳。
-   **`ResponseEntity<T>`**：需要明确控制 HTTP
    状态码、响应头或特殊响应行为时使用；它与自定义结果对象并不互斥。
-   **全局异常处理**：使用 `@RestControllerAdvice` 处理 MVC 异常，使用
    Spring Security 的专用处理器处理认证和授权异常。

**不要将所有错误都返回 HTTP 200，也不要只依赖 HTTP
状态码识别具体业务错误。**

## 2. 推荐响应格式

### 2.1 成功响应

``` json
{
  "status": 200,
  "code": "0",
  "message": "success",
  "data": {
    "id": 1,
    "username": "admin"
  }
}
```

约定：

-   `code = "0"` 表示业务处理成功。
-   `message` 为可读提示；可按项目需要支持国际化。
-   `data` 为业务数据，无数据时为 `null`。
- 成功统一返回 `200 OK`，无业务数据时 `data` 为 `null`。

### 2.2 错误响应

``` json
{
  "status": 400,
  "timestamp": "2026-10-14T18:20:00+08:00",
  "code": "COMMON_1003",
  "message": "用户名已存在",
  "data": null
}
```

约定：

-   HTTP 响应状态与 JSON 的 `status` 一致。
-   `code` 是稳定的业务错误标识。
-   `message` 不暴露 SQL、堆栈、密钥、内部主机信息等敏感细节。
-   `traceId` 用于关联请求与服务端日志。
-   成功与错误使用同一个 `ApiResult` 结构。

## 3. HTTP 状态码规范

  -------------------------------------------------------------------------------------------------------------
  状态码                        典型场景                     说明
  ----------------------------- ---------------------------- --------------------------------------------------
  `200 OK`                      查询、更新成功               有响应体的常规成功响应

  `400 Bad Request`             参数校验失败、请求格式错误   请求本身不符合接口要求

  `401 Unauthorized`            未认证、认证凭据无效         例如缺少或无效的 Token

  `403 Forbidden`               已认证但权限不足             不允许当前主体执行该操作

  `404 Not Found`               路由或目标资源不存在         是否区分资源不存在与权限限制，需考虑信息泄露风险

  `409 Conflict`                与当前资源状态冲突           例如用户名重复、状态冲突

  `500 Internal Server Error`   未预期的服务端异常           不向客户端泄露内部异常细节

  -------------------------------------------------------------------------------------------------------------

### 使用原则

1.  业务失败不等于 HTTP 500。可预期的业务冲突应使用适当的 4xx 状态码。
2.  `401` 表示未成功认证，`403` 表示已认证但没有权限。
3.  不要将所有错误都包装成 HTTP 200；这会误导 HTTP
    客户端、监控和重试机制。
4. 请求方法、媒体类型、文件超限和限流错误统一返回 400；系统及依赖故障返回 500。
5.  状态码选择应在 API 文档中明确，并在整个项目中保持一致。

## 4. 业务错误码规范

### 4.1 命名建议

推荐使用带业务域前缀的字符串：

-   `COMMON_1001`
-   `AUTH_1001`
- `USER_1004`
-   `ORDER_1001`
-   `SYS_1001`

### 4.2 示例错误码

| 错误码           | 含义            |
|---------------|---------------|
| `COMMON_1001` | 请求参数校验失败      |
| `COMMON_1002` | 请求资源不存在（通用）   |
| `AUTH_1001`   | 用户名或密码错误      |
| `AUTH_1002`   | 账号已禁用         |
| `AUTH_1005`   | 未登录、凭证无效或会话失效 |
| `AUTH_1007`   | 验证码无效或已过期     |
| `COMMON_1003` | 数据已存在         |
| `COMMON_1010` | 数据存在关联        |
| `SYS_1001`    | 系统内部异常        |

这些编号是示例，项目应建立统一登记规则。具体场景需结合安全策略和接口语义确定，例如登录失败时，为避免账号枚举风险，可以统一返回"用户名或密码错误"，并使用同一个对外错误码。

### 4.3 错误码设计原则

1.  **唯一性**：一个错误码对应明确、稳定的语义。
2.  **稳定性**：修改提示文案不应随意更改错误码。
3.  **可读性**：优先采用有业务域前缀的字符串，避免无意义的超长数字。
4.  **不携带动态数据**：不要将用户 ID、订单号、时间戳放进错误码。
5.  **避免过度细分**：字段校验可共用一个错误码，再通过结构化字段返回具体字段错误。
6.  **前端依赖错误码而不是消息文本**：消息可能变化或需要国际化。

## 5. Java 17 参考实现

以下为核心示例。实际项目应结合包结构、日志规范和 Spring Boot 版本完善。

### 5.1 成功响应对象 `ApiResult<T>`

``` java
public record ApiResult<T>(
    String code,
    String message,
    T data
) {
    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>("0", "success", data);
    }

    public static ApiResult<Void> success() {
        return new ApiResult<>("0", "success", null);
    }

    public static <T> ApiResult<T> failure(
            String code, String message) {
        return new ApiResult<>(code, message, null);
    }
}
```

建议常规成功接口返回
`ApiResult<T>`。如果需要国际化，可以让消息由统一消息服务解析，而不是将所有文案写死在枚举中。

`ApiResult` 仅封装响应，不进行 HTTP 状态码转换。HTTP 状态由框架层按异常类型和场景，通过 `ResponseEntity` 或安全异常处理器直接设置。

### 5.2 错误码接口

如果希望业务层尽量不依赖 Spring Web，错误码接口不应直接暴露
`HttpStatus`：

``` java
public interface ErrorCode {
    String getCode();
    String getMessage();
}
```

同类业务失败优先使用通用错误码，具体字段和资源由消息说明。仅为有独立处理需求的业务定义专用错误码，例如：

``` java
@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    PASSWORD_INCORRECT("USER_1004", "密码错误");

    private final String code;
    private final String message;
}
```

这里使用 Lombok 的 `@Getter` 和 `@RequiredArgsConstructor`；若不使用
Lombok，可手动实现构造方法和 getter。

### 5.3 业务异常

``` java
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
```

Service 层抛出业务异常：

``` java
if (userRepository.existsByUsername(username)) {
    throw new BusinessException(
        CommonErrorCode.DATA_ALREADY_EXISTS, "用户名已存在"
    );
}
```

不要在业务 Service 中到处构造 `ResponseEntity`，也不要在每个 Controller
中重复编写 `try-catch`。

### 5.4 全局异常处理器

下面示例演示结构；普通业务异常返回 400，其他状态按异常类型和场景设置。

``` java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> handleBusinessException(
            BusinessException ex,
            HttpServletRequest request) {

        ErrorCode error = ex.getErrorCode();

        ApiResult<Void> body = ApiResult.failure(
            error.getCode(), ex.getMessage()
        );

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        return buildError(
            HttpStatus.BAD_REQUEST,
            "COMMON_1001",
            "请求参数校验失败",
            request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleUnexpectedException(
            Exception ex,
            HttpServletRequest request) {

        String traceId = currentTraceId();
        log.error("Unhandled exception, traceId={}", traceId, ex);

        return buildError(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "SYS_1001",
            "系统内部异常",
            request
        );
    }

    private ResponseEntity<ApiResult<Void>> buildError(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request) {

        ApiResult<Void> body = ApiResult.failure(code, message);

        return ResponseEntity.status(status).body(body);
    }

    private String currentTraceId() {
        return Optional.ofNullable(MDC.get("traceId")).orElse("");
    }
}
```

说明：

- 不维护业务错误码到 HTTP 状态的映射；异常处理器根据异常类型设置状态。
-   需要补齐项目实际使用的 import、日志对象和异常类型。
-   应根据 Spring Boot 版本处理
    `MethodArgumentNotValidException`、`ConstraintViolationException`、`HttpMessageNotReadableException`
    等常见异常。
-   不应把所有异常都映射为
    500；已知的认证、授权、参数、资源不存在等异常应有明确映射。
-   `MDC` 的 `traceId`
    必须由追踪组件或过滤器在请求入口处生成/写入，并在请求结束后清理；不能只读取而不建立上下文。

## 6. `ApiResult<T>` 与 `ResponseEntity<T>` 如何选择

两者不是二选一。推荐默认策略如下：

  -----------------------------------------------------------------------
  场景                                推荐返回方式
  ----------------------------------- -----------------------------------
  普通查询、分页查询                  `ApiResult<T>`

  普通更新、删除                      `ApiResult<Void>` 或
                                      `ApiResult<T>`，按团队 API 规范统一

创建资源，返回 HTTP 200             `ApiResult<T>`

  需要设置 `Location` 或其他响应头    `ResponseEntity<ApiResult<T>>`

  文件下载、二进制流                  `ResponseEntity<Resource>`
                                      或适合的流式响应

  统一业务异常                        `ResponseEntity<ApiResult<Void>>`

  Spring Security 认证/授权失败       专用安全异常处理器写入 `ApiResult<Void>`
  -----------------------------------------------------------------------

例如创建资源：

``` java
@PostMapping
public ResponseEntity<ApiResult<UserVO>> create(
        @RequestBody @Valid CreateUserRequest request) {

    UserVO user = userService.create(request);

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(ApiResult.success(user));
}
```

普通查询：

``` java
@GetMapping("/{id}")
public ApiResult<UserVO> getById(@PathVariable Long id) {
    return ApiResult.success(userService.getById(id));
}
```

如果项目决定严格遵循 REST 资源语义，也可以不包装成功响应，直接返回
DTO；但应在项目层面统一，而不是不同接口随意混用。

## 7. Spring Security 必须单独处理

`@RestControllerAdvice` 通常无法覆盖所有发生在 Controller
之前的安全异常。因此应实现：

-   `AuthenticationEntryPoint`：处理未认证、Token
    无效或过期等情况，通常返回 HTTP 401。
-   `AccessDeniedHandler`：处理已认证但权限不足的情况，通常返回 HTTP
    403。

这两个组件应输出与全局异常处理器一致的 `ApiResult` JSON 结构，并确保 HTTP
状态码与 JSON 的 `status` 一致。

还需考虑：

-   登录凭据错误的对外消息应避免账号枚举。
-   Token 过期、无效、缺失的处理应符合项目的认证协议。
-   安全过滤器中的异常要由安全异常处理链正确转换，不应假设它们都会到达
    `@RestControllerAdvice`。

## 8. 推荐模块职责

``` text
common / core
├── response
│   └── ApiResult.java
└── error
    ├── ErrorCode.java
    └── BusinessException.java

user / auth / order 等业务模块
└── error
    └── XxxErrorCode.java

framework / infrastructure（Web 适配）
├── web
│   ├── ApiError.java
│   └── GlobalExceptionHandler.java
└── security
    ├── RestAuthenticationEntryPoint.java
    └── RestAccessDeniedHandler.java
```

模块边界建议：

-   业务错误语义属于对应业务模块。
-   通用异常抽象可放在 `common` 或 `core`。
- HTTP 状态码设置、`ResponseEntity`、Spring MVC 异常处理和 Spring
    Security 适配属于 Web/框架适配层。
-   如果希望 `common` 保持纯 Java，应避免在其公共接口中直接依赖
    `HttpStatus` 等 Spring Web 类型。

## 9. 前端处理规范

1.  根据 HTTP 状态码处理通用协议行为，例如 `401` 触发重新认证流程。
2. 根据业务 `code` 处理对应类别，例如 `COMMON_1003` 表示数据重复，`message` 说明具体字段。
3.  使用 `message`
    展示用户可理解的提示，不要通过解析消息文本判断业务逻辑。
4.  参数校验错误可以增加结构化 `details`
    字段，例如字段名、错误类型和消息。
5.  不应把服务端堆栈、SQL 或内部异常消息直接展示给用户。

## 10. 项目落地检查清单

-   [ ] 统一成功响应 `ApiResult<T>`。
-   [ ] 统一错误响应 `ApiResult<Void>`。
-   [ ] 定义 HTTP 状态码使用规范。
-   [ ] 建立业务错误码登记与唯一性检查规则。
-   [ ] 按异常类型设置 HTTP 状态，无需业务错误码映射。
-   [ ] 使用 `@RestControllerAdvice` 统一处理 MVC 异常。
-   [ ] 使用 `AuthenticationEntryPoint` 和 `AccessDeniedHandler`
    统一处理安全异常。
-   [ ] 统一参数校验异常响应。
-   [ ] 建立并传播 `traceId`，关联响应和日志。
-   [ ] 未知异常对外返回通用消息，服务端记录完整堆栈。
-   [ ] API 文档列出接口可能返回的 HTTP 状态码和业务错误码。
-   [ ] 编写集成测试，验证真实 HTTP 状态、JSON 结构和安全异常行为。

## 11. 最终规范摘要

推荐采用以下约定：

1.  **HTTP 状态码表达协议结果，业务错误码表达具体业务原因。**
2.  **成功和错误响应统一使用 `ApiResult<T>`。**
3.  **需要特殊状态码或响应头时使用 `ResponseEntity<T>`。**
4.  **不将所有错误统一返回 HTTP 200。**
5. **业务层不负责构造 HTTP 响应，Web 适配层按异常类型设置状态码。**
6.  **MVC 异常与 Spring Security 异常都要接入统一错误响应体系。**
7.  **错误码保持稳定、唯一、可读；消息不作为前端逻辑判断依据。**
8.  **通过 `traceId` 实现线上问题的日志追踪。**

这套设计适合以 Spring Boot
为基础的企业管理后台，并能在未来拆分服务时继续沿用。
