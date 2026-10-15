# 持久化模型分层改造指南

> 参考实现：系统管理模块 `Dept` 改造
> 适用技术栈：Java 17、Spring Boot 3.5、MyBatis-Plus、MapStruct、Lombok、Vue 2
> 文档目的：为 `User`、`Role`、`Menu`、`Job` 等其他数据模型的分层改造提供可重复执行的方案。

## 1. 改造目标

改造的核心不是增加 DTO 数量，而是建立稳定的职责边界：

- Controller 只处理 HTTP 协议、权限和参数校验。
- Request 只表达一个接口允许客户端提交的数据。
- Response/DTO 只表达接口允许客户端看到的数据。
- Service 负责业务流程、权限条件、事务、转换和树等结构组装。
- Mapper 只接收持久化模型、主键和数据库查询条件，不接收 Web 请求对象。
- 持久化模型与数据库表一一对应，不承载校验、展示、序列化或业务集合语义。
- 前后端以明确的请求和响应契约协作，不依赖数据库模型的字段变化。

改造后的典型调用链如下：

```text
HTTP request
    -> XxxController
    -> XxxCreate/Update/Query/DeleteRequest
    -> XxxService
    -> XxxAssembler
    -> Xxx persistence model
    -> XxxMapper / MyBatis-Plus Wrapper
    -> database

database
    -> Xxx persistence model
    -> XxxAssembler
    -> XxxDto
    -> HTTP response
```

## 2. 模型职责与命名

| 类型    | 推荐命名                     | 职责                     | 禁止事项                                              |
|-------|--------------------------|------------------------|---------------------------------------------------|
| 持久化模型 | `Xxx`，全项目统一后也可使用 `XxxDO` | 映射数据库表                 | 不用于 Controller 入参或出参，不放校验、Swagger、Jackson 和业务展示字段 |
| 新增请求  | `XxxCreateRequest`       | 定义新增允许提交的字段            | 不包含 ID、审计字段和服务端计算字段                               |
| 修改请求  | `XxxUpdateRequest`       | 定义修改允许提交的字段            | 不直接覆盖完整数据库对象                                      |
| 查询请求  | `XxxQueryRequest`        | 定义客户端可用的筛选条件           | 不包含数据权限 ID 等服务端条件，不传入 Mapper                      |
| 删除请求  | `XxxDeleteRequest`       | 包装待删除 ID 及相关操作参数       | 不直接接收裸集合后再依赖隐式校验                                  |
| 场景请求  | `XxxSuperiorRequest` 等   | 表达特定用例所需参数             | 不为复用而塞入无关字段                                       |
| 响应模型  | `XxxDto`                 | 定义接口返回字段和展示计算属性        | 不添加数据库映射注解                                        |
| 转换器   | `XxxAssembler`           | Request、持久化模型、DTO 之间转换 | 不访问 Mapper，不执行事务和权限判断                             |

`Assembler` 这个名称适合当前项目，因为它不只是复制字段，还可能组装树、子节点或其他响应结构。若转换器仅做一对一字段映射，也可以使用
`XxxConverter`，但同一模块应保持一致。

## 3. `Dept` 改造结果

### 3.1 改造前的主要问题

- `DeptController` 直接接收和返回数据库模型。
- `Dept` 同时包含 MyBatis-Plus、Validation、Swagger、Jackson、Spring Data 和 Lombok 注解。
- `DeptQueryCriteria` 被直接传入 Mapper XML，Web 查询模型与数据库层耦合。
- Controller 负责递归收集删除节点、关联校验和对象去重。
- `Dept.equals/hashCode` 被 `HashSet` 隐式用于去重，数据库模型承担了业务身份语义。
- 非分页的部门树使用 `PageResult` 返回。
- 集合参数使用裸 `List<Long>`、`Set<Long>`，接口含义和校验能力不足。

### 3.2 改造后的模型

```text
Dept                         数据库表模型
DeptCreateRequest            新增请求
DeptUpdateRequest            修改请求
DeptQueryRequest             查询请求
DeptDeleteRequest            删除请求
DeptSuperiorRequest          上级部门树查询请求
DeptDTO                      API 响应
DeptAssembler                MapStruct 转换与部门树组装
```

参考代码：

- [DeptController](../rooe-admin/admin-modules/admin-system/src/main/java/fun/rooe/admin/modules/system/controller/DeptController.java)
- [DeptService](../rooe-admin/admin-modules/admin-system/src/main/java/fun/rooe/admin/modules/system/service/DeptService.java)
- [DeptServiceImpl](../rooe-admin/admin-modules/admin-system/src/main/java/fun/rooe/admin/modules/system/service/impl/DeptServiceImpl.java)
- [DeptAssembler](../rooe-admin/admin-modules/admin-system/src/main/java/fun/rooe/admin/modules/system/converter/DeptAssembler.java)
- [Dept 持久化模型](../rooe-admin/admin-modules/admin-system/src/main/java/fun/rooe/admin/modules/system/domain/Dept.java)
- [部门 Request 与 DTO](../rooe-admin/admin-modules/admin-system/src/main/java/fun/rooe/admin/modules/system/domain/dto)

Controller 最终只保留协议层逻辑：

```java
@GetMapping
public List<DeptDTO> queryDept(@Valid DeptQueryRequest request) {
    return deptService.queryAll(request);
}

@PostMapping("/superior")
public List<DeptDTO> getDeptSuperior(
        @Valid @RequestBody DeptSuperiorRequest request) {
    return deptService.getSuperior(request);
}

@PostMapping
public void createDept(@Valid @RequestBody DeptCreateRequest request) {
    deptService.create(request);
}

@PutMapping
public void updateDept(@Valid @RequestBody DeptUpdateRequest request) {
    deptService.update(request);
}

@DeleteMapping
public void deleteDept(@Valid @RequestBody DeptDeleteRequest request) {
    deptService.delete(request);
}
```

## 4. 持久化模型规范

### 4.1 字段必须与表结构核对

改造前必须读取建表 SQL，逐项核对：

- Java 字段是否对应真实数据库列。
- 类型和可空性是否合理。
- 主键策略是否正确。
- 审计字段是否由自动填充处理。
- 是否存在仅用于展示或关联的 `@TableField(exist = false)` 字段。
- 字段顺序是否与数据库字段顺序一致。
- 每个字段是否有清晰注释。

`Dept` 按 `sys_dept` 的字段顺序整理为：

```text
id, pid, subCount, name, deptSort, enabled,
createBy, updateBy, createTime, updateTime
```

### 4.2 只保留持久化所需注解

持久化模型允许使用：

- `@TableName`
- `@TableId`
- `@TableField`

以下内容应迁移出去：

- `@NotNull`、`@NotBlank` 等校验注解：迁移到 Request。
- `@Schema`：迁移到 Request 或 DTO。
- `@JsonFormat`、`@JSONField`：迁移到 DTO。
- `@CreatedBy`、`@LastModifiedBy`：使用项目已有的 MyBatis-Plus 自动填充方案。
- `children`、`roles` 等展示或关联字段：迁移到 DTO，或通过专用查询返回。
- 校验分组接口：由不同 Request 类型替代。

本改造规范要求持久化模型不使用 Lombok 注解，采用显式 getter/setter。项目虽然使用 Lombok，但不应通过 `@Data` 给数据库模型自动生成
`equals/hashCode/toString`。

### 4.3 不在持久化模型中重写 `equals/hashCode`

数据库对象经常处于以下状态：

- 新对象还没有主键。
- 字段可能在更新过程中变化。
- 同一行可能由不同查询生成不同实例。

因此不应依赖对象相等性完成去重。按数据库身份去重时，应显式使用主键：

```java
Map<Long, Xxx> itemById = new LinkedHashMap<>();
items.

forEach(item ->itemById.

putIfAbsent(item.getId(),item));
List<Xxx> deduplicated = new ArrayList<>(itemById.values());
```

如果业务确实需要值相等语义，应放在值对象或专用业务模型中，而不是数据库模型中。

## 5. Request 与 DTO 设计

### 5.1 按用例拆分 Request

不要用一个“大而全”的请求模型覆盖所有接口。新增和修改通常具有不同约束：

```java
public class XxxCreateRequest {
    @NotBlank
    @Size(max = 255)
    private String name;
}

public class XxxUpdateRequest {
    @NotNull
    @Positive
    private Long id;

    @NotBlank
    @Size(max = 255)
    private String name;
}
```

集合参数也应包装成请求对象，以便表达含义、扩展参数并执行嵌套校验：

```java
public class XxxDeleteRequest {
    @NotEmpty
    private Set<@NotNull @Positive Long> ids;
}
```

不要继续使用：

```java
delete(@RequestBody Set<Long> ids)
```

### 5.2 查询 Request 只包含客户端条件

数据权限 ID、当前用户 ID、租户 ID 等服务端上下文不能放进 Query Request。以部门查询为例，数据权限由 Service 单独获取：

```java
List<Long> dataScopeIds = SecurityUtils.getCurrentUserDataScope();
LambdaQueryWrapper<Dept> wrapper = buildQueryWrapper(
        request, dataScopeIds, dataScopeType);
```

这样可以避免客户端伪造内部过滤条件，也避免为了 Mapper 查询而修改请求对象。

### 5.3 DTO 只包含响应需要的数据

以下内容适合放在 DTO：

- 时间格式化注解。
- Swagger 字段说明。
- `children` 等树结构字段。
- `label`、`leaf`、`hasChildren` 等展示计算属性。

不要为了“以后可能用到”直接复制整张表和所有关联对象。响应字段越多，接口兼容和数据暴露风险越高。

## 6. MapStruct 与 Lombok 配置

当前项目采用：

```xml

<lombok.version>1.18.48</lombok.version>
<mapstruct.version>1.6.3</mapstruct.version>
<lombok-mapstruct-binding.version>0.2.0</lombok-mapstruct-binding.version>
```

同时使用 Lombok 和 MapStruct 时，编译器注解处理器应包含：

```xml
<annotationProcessorPaths>
    <path>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <version>${lombok.version}</version>
    </path>
    <path>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok-mapstruct-binding</artifactId>
        <version>${lombok-mapstruct-binding.version}</version>
    </path>
    <path>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct-processor</artifactId>
        <version>${mapstruct.version}</version>
    </path>
</annotationProcessorPaths>
```

推荐的 Assembler 定义：

```java
@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface XxxAssembler {

    XxxDto toDto(Xxx entity);

    List<XxxDto> toDtos(List<Xxx> entities);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source = "name")
    Xxx toEntity(XxxCreateRequest request);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source = "name")
    void updateEntity(XxxUpdateRequest request,
                      @MappingTarget Xxx entity);
}
```

关键约束：

- 使用 `componentModel = SPRING`，由 Spring 注入。
- 使用 `ReportingPolicy.ERROR`，新增字段未处理时编译失败。
- Request 转实体建议使用 `ignoreByDefault = true`，防止未来新增敏感字段被自动写入。
- 修改操作使用 `@MappingTarget` 更新数据库查出的实体，不用请求对象新建完整实体覆盖数据库记录。
- 树组装等纯内存转换可以作为 Assembler 的 `default` 方法。
- Assembler 不调用 Mapper、缓存、安全上下文或其他 Service。

## 7. 查询层改造

### 7.1 Request 不得透传 Mapper

禁止以下形式：

```java
List<Xxx> findAll(@Param("criteria") XxxQueryRequest request);
```

原因是 Mapper 因此依赖 Web 接口模型，Request 的字段调整会直接影响 SQL 层。

### 7.2 在 Service 构造 MyBatis-Plus Wrapper

简单的单表动态查询统一使用 `LambdaQueryWrapper`：

```java
LambdaQueryWrapper<Xxx> wrapper = Wrappers.lambdaQuery();
wrapper.

in(!CollectionUtils.isEmpty(scopeIds),Xxx::getId,scopeIds)
        .

like(StringUtils.isNotBlank(request.getName()),
Xxx::getName,request.

getName())
        .

eq(request.getEnabled() !=null,
Xxx::getEnabled,request.

getEnabled())
        .

orderByAsc(Xxx::getSort);

List<Xxx> entities = xxxMapper.selectList(wrapper);
```

改造时必须保持原 SQL 的条件组合、空值语义和排序规则，特别注意：

- 空集合是否表示“不限制”还是“无数据”。
- `Boolean` 的 `null/true/false` 是否具有不同含义。
- 时间范围是否必须包含两个值。
- 数据权限条件是否会覆盖客户端条件。
- 原查询是否存在去重、联表或数据库方言行为。

复杂联表、聚合、窗口函数或性能敏感 SQL 可以继续放在 XML 中，但 Mapper 参数应使用数据库层参数或明确的查询对象，不应直接依赖
Controller Request。

## 8. Service 与 Controller 边界

### 8.1 Controller 应保持薄层

Controller 可以负责：

- 路由和 HTTP 参数绑定。
- `@PreAuthorize` 权限声明。
- `@Valid` 参数校验触发。
- 操作日志注解。
- 调用一个清晰的 Service 用例。

Controller 不应负责：

- Request 到实体的字段复制。
- 递归查询和树构建。
- 删除范围计算。
- 关联关系校验。
- 缓存清理。
- 数据权限拼装。
- 利用 `HashSet<实体>` 隐式去重。

### 8.2 Service 负责完整业务用例

删除部门的正确流程是：

```text
DeptDeleteRequest
    -> 收集指定部门及所有子部门
    -> 按部门 ID 显式去重
    -> 校验用户和角色关联
    -> 删除数据库记录
    -> 更新父部门子节点数量
    -> 清理相关缓存
```

上述流程必须在同一个事务边界内完成，Controller 只调用一次 `service.delete(request)`。

## 9. 返回类型设计

返回类型应表达真实语义：

- 分页查询：`PageResult<XxxDto>`。
- 普通列表：`List<XxxDto>`。
- 树：`List<XxxDto>`，节点通过 `children` 表达层级。
- 单对象：`XxxDto`。
- 无数据命令：按项目统一响应规范返回 `void` 或明确结果。

不要因为前端习惯读取 `content`，就把非分页列表包装为 `PageResult`。如果接口从分页包装改为列表，必须同步检查：

- API 请求封装。
- 通用 CRUD 数据适配。
- 页面中的 `res.content`。
- 懒加载树节点。
- 其他复用该接口的页面。

本次部门改造中，通用 CRUD 同时兼容列表和分页响应：

```javascript
crud.page.total = Array.isArray(data) ? data.length : data.totalElements
crud.data = Array.isArray(data) ? data : data.content
```

## 10. API 契约变更示例

集合与附加参数应组装成 Request 对象。

查询上级部门：

```json
{
  "ids": [1, 2],
  "exclude": false
}
```

删除部门：

```json
{
  "ids": [1, 2]
}
```

后端请求结构变化属于 API 契约变化。提交代码前必须搜索并同步所有前端和外部调用方，不能只保证后端编译通过。

## 11. 推荐实施顺序

### 第一步：建立基线

1. 读取建表 SQL 和 Mapper XML。
2. 搜索实体在 Controller、Service、Mapper、缓存、集合和前端中的全部引用。
3. 记录现有请求 JSON、响应 JSON、排序、权限和异常行为。
4. 先执行一次测试和构建，确认改造前基线可用。

### 第二步：定义接口模型

1. 创建 Create、Update、Query、Delete 和场景 Request。
2. 将 Validation 和 Swagger 注解迁移到 Request。
3. 创建或收敛 DTO，将 Jackson 和展示字段迁移到 DTO。
4. 明确分页、列表和树的返回类型。

### 第三步：引入 Assembler

1. 配置 MapStruct、Lombok 和 binding。
2. 实现实体与 DTO 转换。
3. 实现新增 Request 到实体转换。
4. 使用 `@MappingTarget` 实现修改。
5. 将纯内存树组装迁入 Assembler。

### 第四步：收敛 Service

1. Service 直接接收 Request。
2. Service 返回 DTO，不让 Controller 接触数据库模型。
3. 将递归、去重、关联校验、事务和缓存处理下沉。
4. 将数据权限作为服务端条件处理。

### 第五步：清理 Mapper

1. 单表动态查询改为 MyBatis-Plus Wrapper。
2. 删除接收 Request/Criteria 的 Mapper 方法。
3. 删除失效的 XML 动态 SQL。
4. 保留确有必要的复杂 SQL，并核对参数边界。

### 第六步：纯化持久化模型

1. 移除非 MyBatis-Plus 注解。
2. 移除展示和关联字段。
3. 移除校验分组。
4. 移除 `equals/hashCode`。
5. 按数据库列顺序排列字段并补充注释。

### 第七步：同步调用方并验证

1. 更新前端请求体和响应读取逻辑。
2. 搜索旧类型、旧方法和旧 JSON 结构残留。
3. 执行后端测试、前端 lint 和生产构建。
4. 检查 Git 差异，确保没有无关格式化或生成文件。

## 12. 验证命令

后端要求使用 Java 17 或更高版本：

```bash
mvn -pl admin-modules/admin-system -am test
```

前端验证：

```bash
npm run lint -- --no-fix
npm run build:prod
```

残留引用与差异检查：

```bash
rg "XxxQueryCriteria|findAll\(@Param\(\"criteria\"\)|equals\(Object|hashCode\(" rooe-admin
git diff --check
git status --short
```

## 13. 验收清单

### 持久化模型

- [ ] 字段与数据库列一一对应。
- [ ] 字段顺序与建表 SQL 一致。
- [ ] 字段类型、主键和自动填充配置正确。
- [ ] 每个字段都有准确注释。
- [ ] 只保留必要的 MyBatis-Plus 注解。
- [ ] 没有 Validation、Swagger、Jackson、Spring Data 注解。
- [ ] 没有展示字段、树字段和关联集合。
- [ ] 没有重写 `equals/hashCode`。

### 接口层

- [ ] Controller 不接收或返回持久化模型。
- [ ] 每个写操作使用专用 Request。
- [ ] 集合参数包装成 Request 并校验集合和元素。
- [ ] Query Request 不包含数据权限等内部字段。
- [ ] 返回类型与分页、列表、树的真实语义一致。
- [ ] Controller 中没有组装、递归、去重和关联校验逻辑。

### Service 与转换

- [ ] Service 承担完整业务用例和事务边界。
- [ ] MapStruct 使用 Spring 组件模型。
- [ ] 未映射目标字段会导致编译失败。
- [ ] 更新使用 `@MappingTarget`，不会用请求对象覆盖审计字段。
- [ ] 按主键显式去重，不依赖实体相等性。

### Mapper 与查询

- [ ] Mapper 不依赖 Controller Request。
- [ ] 简单单表动态查询使用 Lambda Wrapper。
- [ ] 原查询条件、空值语义、排序和数据权限保持一致。
- [ ] 无效 Mapper 方法和 XML SQL 已删除。

### 兼容与验证

- [ ] 所有前端和外部调用方已同步新请求 JSON。
- [ ] 所有调用方已同步列表或分页响应变化。
- [ ] 后端测试通过。
- [ ] 前端 ESLint 通过。
- [ ] 前端生产构建通过。
- [ ] `git diff --check` 通过。

## 14. 不应机械复制的部分

`Dept` 是树形数据，包含父子递归、上级节点查询、子节点计数和删除子树。其他实体不一定需要这些逻辑：

- 普通字典或岗位数据通常不需要树组装。
- 用户和角色存在多对多关系，可能保留专用联表 Mapper。
- 大数据量树不应直接照搬内存递归，应评估递归 SQL、闭包表、路径字段或批量查询。
- `PageResult` 是否保留应由接口是否真实分页决定，不能统一替换为列表。

应复用的是分层原则、命名、转换边界和验收流程，而不是逐行复制部门实现。
