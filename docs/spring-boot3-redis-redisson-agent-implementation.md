# Spring Boot 3 + Redis + Redisson 集成实施规范

> 本文供 AI Agent
> 在现有项目中直接实施。实施前必须检查仓库结构、依赖版本和现有配置；优先遵循项目现状，不得覆盖已有约定或擅自升级技术栈。

## 1. 目标与范围

在现有 Spring Boot 3、JDK 17 项目中集成 Redis 与
Redisson，形成职责清晰、可维护、可扩展的基础设施能力。

### 1.1 目标

- 普通 Redis 命令使用 Spring Data Redis。
- Spring Data Redis 默认客户端使用 Lettuce，由 Spring Boot 依赖管理。
- 分布式锁等高级能力使用 Redisson。
- 连接参数、序列化和 Bean 配置集中管理。
- 业务代码不散落客户端初始化逻辑。
- 为后续缓存、Spring Security Token/Session 状态等场景提供基础能力。

### 1.2 本次不做

- 不实现完整缓存框架、多级缓存、缓存预热或缓存刷新系统。
- 不实现登录、踢人、多端登录等业务功能；本次仅提供 Redis/Redisson
  基础集成。
- 不引入 JetCache、其他 Redis
  客户端或额外的分布式锁框架，除非仓库已经采用并且有明确迁移需求。
- 不因本次集成重构无关模块。

## 2. 技术选型与职责

  ------------------------------------------------------------------------------------------------
能力 推荐实现
  ----------------------------------- ------------------------------------------------------------
普通 Redis 操作                     `StringRedisTemplate`、必要时使用 `RedisTemplate`

Redis 客户端 Spring Data Redis 默认 Lettuce

分布式锁、信号量等 Redisson `RedissonClient`

配置管理 Spring Boot `application.yml` + 环境变量

归属模块 当前项目的
`admin-infrastructure`；若仓库实际模块名不同，遵循现有结构

Java 17

Spring Boot                         3.x，使用项目当前版本，不擅自升级
------------------------------------------------------------------------------------------------

Spring Data Redis 与 Redisson 是两套客户端
API，可以共存，但会分别维护连接与资源。只创建应用级客户端，不得在业务方法中反复创建连接工厂或
`RedissonClient`。

## 3. 实施前检查

Agent 必须先检查：

1. `pom.xml`、父 POM、依赖 BOM，确认 Spring Boot 具体版本、Java
   版本和依赖管理方式。
2. 当前是否已经引入 `spring-boot-starter-data-redis`、Lettuce、Redisson
   或其他 Redis 客户端。
3. 当前是否已有 Redis/Redisson 配置类、`RedisTemplate`
   Bean、序列化配置或连接池配置。
4. 当前模块结构，确认 `admin-infrastructure`
   或实际基础设施模块的依赖方向。
5. `application.yml`、`application-local.yml`、环境变量模板等配置文件，避免重复键或提交真实密码。
6. 测试框架与现有编码风格。

完成检查后再修改；复用已有实现，避免创建重复 Bean。

## 4. Maven 依赖

### 4.1 Spring Data Redis

在实际承载基础设施实现的模块中添加依赖（若已经存在则不重复添加）：

``` xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

不要手动指定 Spring Data Redis 或 Lettuce
版本，除非项目的依赖管理明确要求。

### 4.2 Redisson

优先选择与当前 Spring Boot 3.x 具体版本兼容的 Redisson
集成方式。实施时查验 Redisson
官方兼容说明与发布版本，不得使用未经核实的占位版本，也不得直接复制过时版本号。

可选方式：

- **优先方案 A：Redisson Spring Boot
  Starter**。适用于当前项目版本兼容、且其自动配置行为能够满足项目需求的情况。
- **方案 B：Redisson Core + 显式配置 Bean**。当 Starter
  与项目现有配置冲突，或需要严格控制客户端创建、配置来源时使用。

Starter 依赖坐标：

``` xml
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <version>${redisson.version}</version>
</dependency>
```

在父 POM 的 `properties` 中维护经验证的版本属性：

``` xml
<properties>
    <redisson.version>经兼容性核验的版本</redisson.version>
</properties>
```

上面的版本值是说明性占位文本，Agent
必须在实施时替换为真实、兼容的版本。若无法核实版本，不得提交不可构建的
POM；应检查项目已有版本、官方发布说明及依赖树后再决定。

如果选择显式配置 Bean，则使用 `org.redisson:redisson`，并且不要同时引入
Starter 来创建第二套客户端。

### 4.3 依赖原则

- 不要显式重复引入 `lettuce-core`，除非有明确的版本或依赖管理理由。
- 不要同时使用 Starter 自动创建的 `RedissonClient` 与手工创建的另一个
  `RedissonClient`。
- 完成后检查依赖树，排查 Spring Boot、Netty、Jackson
  等依赖的版本冲突。
- 将版本统一放在项目既有的依赖管理位置，不要在多个模块分别硬编码版本。

## 5. Redis 连接配置

在现有配置文件中合并配置，不得覆盖其他业务配置：

``` yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      database: ${REDIS_DATABASE:0}
      password: ${REDIS_PASSWORD:}
      timeout: 3s
      connect-timeout: 3s
      client-type: lettuce
```

注意：

- Spring Boot 3 使用 `spring.data.redis` 前缀。
- `localhost`
  仅用于本地开发默认值；生产环境应通过环境变量、密钥管理系统或部署平台注入真实配置。
- 不得提交生产 Redis 密码、密钥或其他凭据。
- 超时值应结合实际环境与 SLA 调整。
- 如果现有部署使用 Sentinel、Cluster、TLS 或
  ACL，应保留并正确配置对应拓扑与认证方式；不得将单机配置强行用于集群环境。
- 不要同时在多个配置源中维护互相冲突的 host、password、database。

## 6. Redisson 配置策略

### 6.1 优先复用配置

优先确认当前采用的 Redisson Starter 版本是否支持从现有 Spring Redis
属性自动配置。不要假定所有版本都具有相同的自动配置行为。

如果 Starter
自动配置满足需求，优先复用现有连接配置，不要再创建重复的客户端 Bean
或另一套不一致的 Redis 连接参数。

### 6.2 独立 Redisson 配置

只有在需要独立的连接池、超时或拓扑参数，或 Starter
自动配置无法满足项目要求时，才使用独立 Redisson 配置。Agent
必须明确配置文件由谁加载，不能仅把文件放进 `resources`
就假定会自动读取。

单机配置示例（仅供需要独立配置时使用）：

``` yaml
singleServerConfig:
  address: "redis://${REDIS_HOST:localhost}:${REDIS_PORT:6379}"
  database: 0
  connectionMinimumIdleSize: 4
  connectionPoolSize: 16
  timeout: 3000
  connectTimeout: 3000
```

该示例不包含密码。若需要认证，应按当前 Redisson
版本支持的字段和项目安全规范配置，并使用环境变量或密钥管理系统，不得把真实密码写入仓库。

连接池参数必须结合应用实例数、Redis
最大客户端连接数和实际并发量评估，不要机械照搬示例值。

### 6.3 客户端生命周期

- `RedissonClient` 应由 Spring 容器统一管理，应用关闭时正确释放资源。
- 不得在每次请求或每次业务调用中创建/关闭客户端。
- 不得同时注册多个相同用途且互相竞争的 `RedissonClient` Bean。
- 若选择手动配置，优先使用 Spring 生命周期管理（例如
  `@Bean(destroyMethod = "shutdown")`），并在启动时验证配置有效。

## 7. RedisTemplate 与序列化

### 7.1 默认建议

- 字符串、计数器、简单状态和可读缓存值优先使用 `StringRedisTemplate`。
- 只有在确实需要对象读写时才添加 `RedisTemplate`。
- 对象序列化必须明确指定，不使用默认 JDK 原生序列化。
- 对跨服务、长期存储或需要兼容升级的数据，优先使用显式 JSON
  DTO/Schema，避免依赖任意 Java 类名或动态类型元数据。

### 7.2 可选 JSON RedisTemplate

如果项目确实需要 `RedisTemplate<String, Object>`，可以基于项目已使用的
Jackson 版本配置 JSON 序列化器。示例仅作实现方向，Agent 应核验当前
Spring Data Redis 版本中序列化器 API 是否适用：

``` java
@Configuration
public class RedisTemplateConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();

        // 根据项目 Jackson 版本配置安全、明确的 JSON 序列化策略。
        // 不要为不可信输入启用不受限制的多态反序列化。
        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();

        return template;
    }
}
```

重要事项：

- 如果项目中的 `ObjectMapper` 含有面向 HTTP API
  的特殊模块或全局序列化策略，应评估其是否适合 Redis
  数据；必要时使用独立、明确配置的 Redis `ObjectMapper`。
- 不要为了让任意对象"自动反序列化"而无条件开启默认类型信息或不受限制的多态反序列化。
- 若项目没有对象读写需求，不要为了形式完整而增加该 Bean。
- 不要覆盖 Spring Boot 已提供的 `StringRedisTemplate`。

## 8. 包结构与分层

如果当前项目模块名为 `admin-infrastructure`，建议将 Redis
能力放在该模块：

``` text
admin-infrastructure
└── src/main/java/.../infrastructure/redis
    ├── config
    │   └── RedisTemplateConfig.java       # 仅在确有需要时添加
    ├── service
    │   └── RedisService.java              # 可选的轻量封装
    └── lock
        └── DistributedLockService.java    # 只有需要统一锁抽象时添加
```

按需创建，不要为了匹配示例而增加空包或无实际价值的包装层。

分层原则：

- Redis/Redisson 技术配置放在基础设施层。
- 业务层不负责创建客户端、连接工厂或配置序列化器。
- 如果项目已有锁接口或缓存接口，业务层优先依赖既有抽象，由基础设施实现。
- 不要让领域层直接依赖 `RedisTemplate`、`RedissonClient`
  等具体技术类型，除非项目已有明确约定。
- 本次不额外创建通用缓存框架或新的 Starter。

## 9. 使用示例

### 9.1 普通字符串操作

``` java
@Service
@RequiredArgsConstructor
public class RedisService {

    private final StringRedisTemplate stringRedisTemplate;

    public void set(String key, String value, Duration ttl) {
        stringRedisTemplate.opsForValue().set(key, value, ttl);
    }

    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }
}
```

注意：仅在项目使用 Lombok 时采用
`@RequiredArgsConstructor`；否则使用构造器注入。`ttl`
应校验为有效的正时长，并遵守项目的 key 命名规范。

### 9.2 Redisson 分布式锁

如项目需要统一锁封装，可参考以下基本模式：

``` java
RLock lock = redissonClient.getLock("lock:order:" + orderNo);
boolean acquired = false;

try {
    acquired = lock.tryLock(0, 30, TimeUnit.SECONDS);
    if (!acquired) {
        throw new IllegalStateException("业务正在处理中");
    }

    // 执行业务逻辑

} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    throw new IllegalStateException("获取分布式锁时线程被中断", e);
} finally {
    if (acquired && lock.isHeldByCurrentThread()) {
        lock.unlock();
    }
}
```

使用约束：

- 锁 key 必须包含业务标识，并遵循统一命名规范。
- 必须在 `finally` 中释放锁，并确认当前线程持有锁。
- 等待时间、租约时间需按业务耗时和失败策略设计，不得盲目使用固定值。
- 固定租约时间可能在业务执行超时后提前释放锁；需要时应评估 Redisson
  watchdog 行为及其适用条件。
- 不得在持有锁期间执行无上限等待或长时间阻塞操作。
- 分布式锁不能替代数据库事务、唯一索引、乐观锁和幂等控制。
- 对支付、退款、收单等资金相关操作，必须将幂等键、状态机、数据库约束和渠道请求幂等纳入整体设计，不能仅依靠
  Redis 锁保证资金安全。
- 不要把 `RLock`
  暴露给业务层作为默认做法；若项目存在统一锁抽象，应在基础设施中适配
  Redisson。

## 10. 异常、可观测性与安全

- Redis
  不可用时的行为应由业务场景决定：缓存可考虑降级，认证状态、幂等记录等关键状态不可未经评估就静默放行。
- 对连接失败、超时和命令异常提供必要日志，但不要输出密码、Token
  或完整敏感数据。
- 不要捕获异常后直接忽略。
- 根据项目已有 Actuator/监控方案接入健康检查；不要为了 Redis
  集成擅自引入一整套新监控系统。
- 生产 Redis 应限制网络访问，启用适当的认证和传输安全策略。
- key 应具有业务前缀、明确的命名规则与合理
  TTL；不要在生产业务中随意使用 `KEYS` 等可能阻塞 Redis 的操作。
- 对可能导致大 key、热点 key 或高频访问的功能进行容量评估。

## 11. 测试要求

至少补充以下验证，按项目已有测试体系实现：

1. Spring 上下文能够正常启动，Redis 相关 Bean 无冲突。
2. `StringRedisTemplate` 可完成写入、读取和 TTL 设置。
3. 若提供 JSON `RedisTemplate`，验证写入/读取与预期序列化格式一致。
4. `RedissonClient`
   能够正常初始化；在集成测试环境验证获取锁、释放锁及竞争失败行为。
5. Redis 不可用时，应用启动和业务调用行为符合项目预期。
6. 不同 profile 的配置能够正确覆盖默认配置，仓库中没有真实凭据。
7. 依赖树中不存在因本次改动引入的明显 Spring Boot、Netty、Jackson
   版本冲突。

集成测试依赖真实 Redis 或项目已有的容器化测试方案。不要把仅靠 Mock
的测试描述为已验证真实 Redis 连接。

## 12. Agent 实施步骤

1. **检查现状**：阅读 POM、模块结构、配置文件、现有 Redis/Redisson
   代码和测试。
2. **确定依赖**：确认当前 Spring Boot 3.x 版本，核验兼容的 Redisson
   版本，使用项目统一版本管理。
3. **合并配置**：添加缺失的 Redis 配置，保留现有环境配置和拓扑。
4. **配置客户端**：优先使用受支持的 Starter
   自动配置；确有必要时再显式配置 Redisson。
5. **配置序列化**：优先使用
   `StringRedisTemplate`；仅在存在对象存储需求时配置 JSON
   `RedisTemplate`。
6. **按需封装**：遵循现有模块边界，不添加无需求的抽象层。
7. **补充测试**：验证启动、读写、TTL、锁生命周期及配置安全。
8. **执行构建**：运行项目规定的测试与构建命令，检查依赖树和启动日志。
9. **提交实施报告**：列出变更文件、依赖版本及选择理由、配置项、执行过的命令、测试结果和未完成事项。

## 13. 验收标准

-   [ ] 项目仍使用 JDK 17 和现有 Spring Boot 3.x 版本。
-   [ ] Maven 依赖版本已核验，不存在占位版本。
-   [ ] 普通 Redis 操作使用 Spring Data Redis。
-   [ ] Redisson 客户端由 Spring 统一管理，未重复创建。
-   [ ] Redis 配置使用 `spring.data.redis`，并支持环境变量注入。
-   [ ] 没有提交真实密码或其他敏感凭据。
-   [ ] 未启用不受限制的反序列化策略。
-   [ ] 没有重复注册或冲突的 Redis/Redisson Bean。
-   [ ] 关键操作有测试或明确记录受环境限制未能验证的部分。
-   [ ] 构建与测试结果已如实汇报。
-   [ ] 未引入与本次需求无关的框架、模块或大规模重构。

## 14. 官方参考

- Spring Boot 3.3 Redis
  文档：https://docs.spring.io/spring-boot/3.3/reference/data/nosql.html
- Redisson 官方仓库与文档入口：https://github.com/redisson/redisson
- Spring Data Redis
  文档：https://docs.spring.io/spring-data/redis/reference/

> 最终实施以仓库当前 Spring Boot 版本、Redisson
> 对应版本的官方文档和现有架构为准。示例代码是实施参考，Agent
> 必须完成兼容性核验、编译和测试，不能只复制代码后宣称完成。
