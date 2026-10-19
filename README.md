<h1 style="text-align: center">ROOE ADMIN 后台管理系统</h1>

#### 项目简介

一个基于 Spring Boot 3.5.16、Mybatis-Plus、Spring Security、Redis 不透明会话和 Vue 的前后端分离后台管理系统

**开发文档：**  [https://rooe-admin.vip](https://rooe-admin.vip)

#### 工程规范

- [持久化模型分层改造指南](docs/persistence-model-refactoring-guide.md)：以 `Dept` 为参考，说明
  Request、DTO、Service、Assembler、Mapper 与数据库模型的拆分方式。
- [统一响应与错误码设计规范](docs/spring-boot-api-response-error-code-best-practices.md)
- [认证与安全 v7 实施交付说明](docs/rooe-admin-auth-v7-delivery.md)

**体验地址：**  [https://rooe-admin.vip/demo](https://rooe-admin.vip/demo)

**本地初始化：** 通过 `AUTH_INITIAL_PASSWORD` 配置安全初始密码；历史演示密码账户会被迁移禁用，首次登录后必须改密。

#### 项目源码

| github                                 |   gitee  |
|--------------------------------------| --- |
| https://github.com/elunez/rooe-admin-mp |  https://gitee.com/elunez/rooe-admin-mp   |

#### VPS推荐
<a href="https://bwh81.net/aff.php?aff=70876" target="_blank">
<img src="https://rooe-admin.vip/images/banner/side.jpeg" style="width: 435px;border-radius: 2px;" alt="帮瓦工">
</a>

使用优惠码: `BWHNCXNVXV`，可获得 6.81% 的折扣， [查看介绍](https://rooe-admin.vip/pages/040101/)
#### 主要特性
- 使用最新技术栈，社区资源丰富。
- 支持数据字典，可方便地对一些状态进行管理
- 支持接口限流，避免恶意请求导致服务层压力过大
- 支持接口级别的功能权限与数据权限，可自定义操作
- 自定义权限注解与匿名接口注解，可快速对接口拦截与放行
- 对一些常用地前端组件封装：表格数据请求、数据字典等
- 前后端统一异常拦截处理，统一输出异常，避免繁琐的判断
- 支持会话空闲与绝对过期、认证版本撤销与管理操作越权防护
- 支持运维管理，可方便地对远程服务器的应用进行部署与管理

####  系统功能

- 用户管理：新增与重置密码使用环境配置的初始密码，并要求用户首次改密
- 角色管理：对权限与菜单进行分配，可根据部门设置角色的数据权限
- 菜单管理：已实现菜单动态路由，后端可配置化，支持多级菜单
- 部门管理：可配置系统组织架构，树形表格展示
- 岗位管理：配置各个部门的职位
- 字典管理：可维护常用一些固定的数据，如：状态，性别等
- 系统日志：记录用户操作日志与异常日志，方便开发人员定位排错
- SQL监控：采用druid 监控数据库访问性能，默认用户名admin，密码123456
- 定时任务：整合Quartz做定时任务，加入任务日志，任务运行情况一目了然
- 邮件工具：配合富文本，发送html格式的邮件
- 亚马逊S3云存储：支持市面上大多数对象存储，兼容亚马逊S3协议，如七牛云，阿里云等
- 支付宝支付：整合了支付宝支付并且提供了测试账号，可自行测试
- 服务监控：监控服务器的负载情况
- 运维管理：一键部署你的应用

#### 项目结构
项目采用按功能分模块的开发方式，结构如下

- `rooe-admin-common` 为系统的公共模块，提供注解、基础实体、异常与响应对象、通用工具类

- `admin-framework` 为框架与基础设施模块，通过 `fun.rooe.admin.framework` 和 `fun.rooe.admin.infrastructure` 两个根包分别管理应用运行支撑和外部资源适配

- `admin-modules` 为功能模块聚合模块，统一管理 `admin-system` 与 `admin-logging`

- `admin-modules/admin-system` 为系统业务模块，提供用户、角色、菜单、登录、定时任务等业务能力，以普通 JAR 供启动模块使用

- `rooe-admin-bootstrap` 为应用启动模块，包含启动类、环境配置、日志配置与应用组装依赖，是最终打包部署的模块

- `admin-modules/admin-logging` 为系统的日志模块，其他模块如果需要记录日志需要引入该模块

#### 详细结构

```
- rooe-admin-common 公共模块
    - base 提供了 Entity 基类
    - exception 通用异常
    - response 统一 ApiResult 响应对象
    - utils 系统通用工具类，列举一些常用的工具类
        - BigDecimaUtils 金额计算工具类
        - StringUtils 字符串工具类
        - EncryptUtils 加密工具类
        - FileUtil 文件工具类
- rooe-admin-framework 框架模块
    - fun.rooe.admin.framework 应用运行支撑
        - annotation、aspect 框架注解与限流切面
        - config Web、Swagger、异步线程池、RSA、权限与 Spring Security 配置
        - exception 全局异常处理
        - utils 请求、安全与 Spring Bean 工具
    - fun.rooe.admin.infrastructure 外部资源适配
        - config Redis、Redisson、MyBatis-Plus、Druid、Quartz、S3 与文件路径配置
        - cache、utils Redis 缓存适配与分页工具
        - mapper 数据库访问接口与 XML
        - service 邮件、S3 与本地文件服务
        - controller 基础设施管理接口
- rooe-admin-bootstrap 应用启动模块
    - RooeAdminApplication Spring Boot 启动入口（fun.rooe.admin 包）
    - resources/config application.yml 及 dev、prod、quartz 环境配置
    - resources 日志配置 logback.xml、SQL 日志配置 spy.properties、启动 banner
    - test 应用启动及首页接口集成测试
- admin-modules 功能模块聚合
    - admin-system 系统业务模块
        - sysrunner 程序启动后处理数据
        - modules 系统相关模块(登录授权、系统监控、定时任务、系统模块、运维模块)
    - admin-logging 系统日志模块
```

框架与基础设施代码统一位于 `admin-framework` 模块，通过根包隔离职责。日志模块依赖该模块提供的框架注解与请求上下文能力；`rooe-admin-system` 依赖 `admin-framework` 和日志模块，`rooe-admin-bootstrap` 负责最终组装；业务模块不依赖 bootstrap。启动类保持在 `fun.rooe.admin` 包，组件扫描和 MyBatis 自动扫描可覆盖各包。

用户实体、用户查询、权限与数据范围组装、登录控制器、认证缓存及定时任务业务保留在 `admin-modules/admin-system`。框架通过
`AuthIdentityService` 获取身份与权限，通过 `SessionService` 原子校验和续期 Redis 不透明会话；`TokenService` 只负责随机令牌和摘要。
`ApiResult` 作为共享响应类型保留在公共模块。

仓库根目录的 `pom.xml` 聚合 `rooe-starter` 与 `rooe-admin`，两者保留各自的父 POM。使用 JDK 17 或更高版本，在仓库根目录执行
`mvn clean verify`；只构建应用及其依赖执行 `mvn -pl rooe-admin/admin-bootstrap -am clean package`。Maven 会同时构建所需
starter，无需提前 install 或发布到远程仓库。IDE 也应导入仓库根 POM。仅验证框架模块可执行
`mvn -pl rooe-admin/admin-framework -am test`。bootstrap 完整启动测试需要可用的数据库与 Redis；无外部服务时可执行
`mvn clean verify "-Dtest=*,!RooeAdminBootstrapApplicationTests" "-Dsurefire.failIfNoSpecifiedTests=false"`，仍会运行
starter 与 admin 的日志、追踪集成测试。业务 SQL 与任务执行需单独验证。

`admin-framework` 引入 `rooe-observability-spring-boot-starter`，`admin-bootstrap` 通过框架模块传递获得该依赖，版本由
admin 父 POM 导入的 `rooe-starter-dependencies` BOM 管理。starter 负责请求与响应日志、脱敏、追踪及响应 traceId；应用保留
`WEB_LOG_ENABLED` 开关和专属路径排除配置。BOM 不覆盖应用的 Spring Boot 版本。单独构建 `rooe-admin` 前需先执行
`mvn -f rooe-starter/pom.xml install`。

部署入口为 `java -jar rooe-admin/admin-bootstrap/target/admin-bootstrap-1.1.jar`，可追加 `--spring.profiles.active=prod`
指定环境。开发时运行 bootstrap 中的 `fun.rooe.admin.RooeAdminApplication`；安装各模块后也可执行
`mvn -pl rooe-admin/admin-bootstrap spring-boot:run`。Mapper XML 与业务邮件模板继续随所属模块的 JAR 加载，应用配置集中维护在
`admin-bootstrap/src/main/resources`。

#### 特别鸣谢

- 感谢 [PanJiaChen](https://github.com/PanJiaChen/vue-element-admin) 大佬提供的前端模板

- 感谢 [Moxun](https://github.com/moxun1639) 大佬提供的前端 Curd 通用组件

- 感谢 [zhy6599](https://gitee.com/zhy6599) 大佬提供的后端运维管理相关功能

- 感谢 [j.yao.SUSE](https://github.com/everhopingandwaiting) 大佬提供的匿名接口与Redis限流等功能

#### 项目捐赠
项目的发展离不开你的支持，请作者喝杯咖啡吧☕  [Donate](https://rooe-admin.vip/pages/030101/)

#### 反馈交流
- QQ交流群：891137268 、947578238、659622532
