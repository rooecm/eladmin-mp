package fun.rooe.admin.modules.system.service.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.rooe.admin.enums.DataScopeEnum;
import fun.rooe.admin.infrastructure.utils.RedisUtils;
import fun.rooe.admin.model.UserDataScope;
import fun.rooe.admin.modules.system.domain.entity.DeptDO;
import fun.rooe.admin.modules.system.domain.entity.RoleDO;
import fun.rooe.admin.modules.system.mapper.DeptMapper;
import fun.rooe.admin.modules.system.mapper.RoleMapper;
import fun.rooe.admin.modules.system.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataServiceImplTest {
    private RedisUtils redisUtils;
    private RoleMapper roleMapper;
    private DeptMapper deptMapper;
    private DataServiceImpl service;
    // 与 Spring Boot 默认配置一致：忽略未知属性
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @BeforeEach
    void setUp() {
        redisUtils = mock(RedisUtils.class);
        roleMapper = mock(RoleMapper.class);
        deptMapper = mock(DeptMapper.class);
        service = new DataServiceImpl(
                redisUtils,
                roleMapper,
                deptMapper,
                mock(UserMapper.class));
        when(redisUtils.get(anyString(), eq(UserDataScope.class))).thenReturn(null);
    }

    @Test
    void userWithoutRolesHasNoDataAccess() {
        when(roleMapper.findByUserId(1L)).thenReturn(List.of());

        UserDataScope scope = service.getDataScope(1L, 10L);

        assertTrue(scope.isNone());
    }

    @Test
    void allScopeIsExplicitInsteadOfAnEmptyListConvention() {
        when(roleMapper.findByUserId(1L)).thenReturn(List.of(role(DataScopeEnum.ALL)));

        UserDataScope scope = service.getDataScope(1L, 10L);

        assertTrue(scope.isAll());
        assertTrue(scope.getDeptIds().isEmpty());
    }

    @Test
    void deptScopeContainsOnlyTheUsersDepartment() {
        when(roleMapper.findByUserId(1L)).thenReturn(List.of(role(DataScopeEnum.DEPT)));

        UserDataScope scope = service.getDataScope(1L, 10L);

        assertTrue(scope.isLimited());
        assertEquals(List.of(10L), scope.getDeptIds());
    }

    @Test
    void customScopeWithoutDepartmentsHasNoDataAccess() {
        when(roleMapper.findByUserId(1L)).thenReturn(List.of(role(DataScopeEnum.CUSTOM)));

        UserDataScope scope = service.getDataScope(1L, 10L);

        assertTrue(scope.isNone());
    }

    @Test
    void deptAndChildScopeExpandsTheUsersDepartment() {
        DeptDO child = dept(20L);
        when(roleMapper.findByUserId(1L)).thenReturn(
                List.of(role(DataScopeEnum.DEPT_AND_CHILD)));
        when(deptMapper.selectList(any())).thenReturn(List.of(child), List.of());

        UserDataScope scope = service.getDataScope(1L, 10L);

        assertEquals(List.of(10L, 20L), scope.getDeptIds());
    }

    @Test
    void selfScopeOnlyIncludesTheCurrentUser() {
        when(roleMapper.findByUserId(1L)).thenReturn(List.of(role(DataScopeEnum.SELF)));

        UserDataScope scope = service.getDataScope(1L, 10L);

        assertTrue(scope.isSelf());
        assertTrue(scope.permitsUser(1L, 99L));
        assertTrue(scope.permitsDeptRead(10L));
    }

    @Test
    void customScopeContainsOnlyExplicitRoleDepartments() {
        when(roleMapper.findByUserId(1L)).thenReturn(List.of(role(DataScopeEnum.CUSTOM)));
        when(deptMapper.findByRoleId(1L)).thenReturn(Set.of(dept(10L)));

        UserDataScope scope = service.getDataScope(1L, 99L);

        assertEquals(List.of(10L), scope.getDeptIds());
    }

    @Test
    void multipleRolesCombineDepartmentAndSelfAccess() {
        when(roleMapper.findByUserId(1L)).thenReturn(List.of(
                role(1L, DataScopeEnum.CUSTOM), role(2L, DataScopeEnum.SELF)));
        when(deptMapper.findByRoleId(1L)).thenReturn(Set.of(dept(10L)));

        UserDataScope scope = service.getDataScope(1L, 99L);

        assertTrue(scope.isLimited());
        assertTrue(scope.permitsUser(2L, 10L));
        assertTrue(scope.permitsUser(1L, 99L));
    }

    @Test
    void dataScopeSurvivesCacheSerialization() throws Exception {
        UserDataScope original = UserDataScope.limited(List.of(10L, 20L), 1L, 30L);

        UserDataScope restored =
                objectMapper.readValue(objectMapper.writeValueAsString(original), UserDataScope.class);

        assertEquals(original, restored);
    }

    @Test
    void expandsSelectedDepartmentsBeforeGrantValidation() {
        DeptDO child = new DeptDO();
        child.setId(20L);
        when(deptMapper.selectList(any())).thenReturn(List.of(child), List.of());

        assertEquals(List.of(10L, 20L), List.copyOf(service.expandDeptIds(List.of(10L))));
    }

    private RoleDO role(DataScopeEnum dataScope) {
        return role(1L, dataScope);
    }

    private RoleDO role(Long id, DataScopeEnum dataScope) {
        RoleDO role = new RoleDO();
        role.setId(id);
        role.setName("role");
        role.setDataScope(dataScope.getValue());
        return role;
    }

    private DeptDO dept(Long id) {
        DeptDO dept = new DeptDO();
        dept.setId(id);
        return dept;
    }
}
