package fun.rooe.admin.modules.system.service.impl;

import fun.rooe.admin.enums.DataScopeEnum;
import fun.rooe.admin.infrastructure.utils.RedisUtils;
import fun.rooe.admin.modules.system.converter.DeptAssembler;
import fun.rooe.admin.modules.system.converter.MenuAssembler;
import fun.rooe.admin.modules.system.converter.RoleAssembler;
import fun.rooe.admin.modules.system.domain.entity.DeptDO;
import fun.rooe.admin.modules.system.domain.entity.RoleDO;
import fun.rooe.admin.modules.system.mapper.*;
import fun.rooe.admin.modules.system.service.DataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;

class RoleServiceImplDataScopeTest {
    private RoleMapper roleMapper;
    private DeptMapper deptMapper;
    private DataService dataService;
    private RoleServiceImpl service;

    @BeforeEach
    void setUp() {
        roleMapper = mock(RoleMapper.class);
        deptMapper = mock(DeptMapper.class);
        dataService = mock(DataService.class);
        service = new RoleServiceImpl(
                roleMapper,
                mock(RoleDeptMapper.class),
                mock(RoleMenuMapper.class),
                mock(UserRoleMapper.class),
                deptMapper,
                mock(MenuMapper.class),
                mock(UserMapper.class),
                mock(RedisUtils.class),
                mock(fun.rooe.admin.modules.security.service.AdminPrivilegeGuard.class),
                mock(fun.rooe.admin.modules.security.service.AuthCacheInvalidator.class),
                dataService,
                mock(RoleAssembler.class),
                mock(DeptAssembler.class),
                mock(MenuAssembler.class));
    }

    @Test
    void deptAndChildRoleChecksTheExpandedTargetDepartment() {
        RoleDO role = role(1L, DataScopeEnum.DEPT_AND_CHILD);
        when(roleMapper.selectBatchIds(Set.of(1L))).thenReturn(List.of(role));
        when(dataService.expandDeptIds(List.of(10L))).thenReturn(Set.of(10L, 20L));

        service.checkDataScopes(Set.of(1L), 10L);

        verify(dataService).checkDeptAccess(Set.of(10L, 20L));
    }

    @Test
    void customRoleChecksOnlyExplicitRoleDepartments() {
        RoleDO role = role(1L, DataScopeEnum.CUSTOM);
        DeptDO dept = new DeptDO();
        dept.setId(10L);
        when(roleMapper.selectBatchIds(Set.of(1L))).thenReturn(List.of(role));
        when(deptMapper.findByRoleId(1L)).thenReturn(Set.of(dept));

        service.checkDataScopes(Set.of(1L), 99L);

        verify(dataService).checkDeptAccess(List.of(10L));
        verify(dataService, never()).expandDeptIds(List.of(10L));
    }

    @Test
    void selfRoleDoesNotRequireDepartmentManagementAccess() {
        RoleDO role = role(1L, DataScopeEnum.SELF);
        when(roleMapper.selectBatchIds(Set.of(1L))).thenReturn(List.of(role));

        service.checkDataScopes(Set.of(1L), 10L);

        verify(dataService, never()).checkDeptAccess(10L);
    }

    private RoleDO role(Long id, DataScopeEnum dataScope) {
        RoleDO role = new RoleDO();
        role.setId(id);
        role.setName("role");
        role.setDataScope(dataScope.getValue());
        return role;
    }
}
