package fun.rooe.admin.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import fun.rooe.admin.constant.CacheKey;
import fun.rooe.admin.enums.DataScopeEnum;
import fun.rooe.admin.error.CommonErrorCode;
import fun.rooe.admin.exception.BusinessException;
import fun.rooe.admin.framework.utils.SecurityUtils;
import fun.rooe.admin.infrastructure.utils.RedisUtils;
import fun.rooe.admin.model.UserDataScope;
import fun.rooe.admin.modules.security.domain.dto.AuthorityDTO;
import fun.rooe.admin.modules.security.service.AdminPrivilegeGuard;
import fun.rooe.admin.modules.security.service.AuthCacheInvalidator;
import fun.rooe.admin.modules.system.converter.DeptAssembler;
import fun.rooe.admin.modules.system.converter.MenuAssembler;
import fun.rooe.admin.modules.system.converter.RoleAssembler;
import fun.rooe.admin.modules.system.domain.dto.RoleDTO;
import fun.rooe.admin.modules.system.domain.dto.UserLoginDTO;
import fun.rooe.admin.modules.system.domain.entity.*;
import fun.rooe.admin.modules.system.domain.request.*;
import fun.rooe.admin.modules.system.mapper.*;
import fun.rooe.admin.modules.system.service.DataService;
import fun.rooe.admin.modules.system.service.RoleService;
import fun.rooe.admin.response.PageResult;
import fun.rooe.admin.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Coordinates role persistence, data scopes, menu assignments, and authorization caches.
 */
@Service
@RequiredArgsConstructor
public class RoleServiceImpl extends ServiceImpl<RoleMapper, RoleDO> implements RoleService {
    private final RoleMapper roleMapper;
    private final RoleDeptMapper roleDeptMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final UserRoleMapper userRoleMapper;
    private final DeptMapper deptMapper;
    private final MenuMapper menuMapper;
    private final UserMapper userMapper;
    private final RedisUtils redisUtils;
    private final AdminPrivilegeGuard privilegeGuard;
    private final AuthCacheInvalidator authInvalidator;
    private final DataService dataService;
    private final RoleAssembler assembler;
    private final DeptAssembler deptAssembler;
    private final MenuAssembler menuAssembler;

    @Override
    public List<RoleDTO> queryAll() {
        return assemble(
                roleMapper.selectList(Wrappers.<RoleDO>lambdaQuery().orderByAsc(RoleDO::getLevel)));
    }

    @Override
    public PageResult<RoleDTO> queryAll(RoleQueryRequest request) {
        Page<RoleDO> page = new Page<>(request.getPage(), request.getSize());
        roleMapper.selectPage(page, wrapper(request));
        return new PageResult<>(assemble(page.getRecords()), page.getTotal());
    }

    private LambdaQueryWrapper<RoleDO> wrapper(RoleQueryRequest request) {
        LambdaQueryWrapper<RoleDO> wrapper = Wrappers.lambdaQuery();
        wrapper.and(
                        StringUtils.isNotBlank(request.getBlurry()),
                        query ->
                                query.like(RoleDO::getName, request.getBlurry())
                                        .or()
                                        .like(RoleDO::getDescription, request.getBlurry()))
                .orderByAsc(RoleDO::getLevel);
        if (request.getCreateTime() != null && request.getCreateTime().size() >= 2)
            wrapper.between(
                    RoleDO::getCreateTime,
                    request.getCreateTime().get(0),
                    request.getCreateTime().get(1));
        return wrapper;
    }

    private List<RoleDTO> assemble(List<RoleDO> roles) {
        List<RoleDTO> result = assembler.toDtos(roles);
        for (RoleDTO dto : result) {
            DataScopeEnum dataScope = DataScopeEnum.find(dto.getDataScope());
            if (dataScope != null) {
                dto.setDataScope(dataScope.getValue());
            }
            dto.setDepts(
                    new LinkedHashSet<>(
                            deptAssembler.toDtos(
                                    new ArrayList<>(deptMapper.findByRoleId(dto.getId())))));
            dto.setMenus(
                    new LinkedHashSet<>(
                            menuAssembler.toDtos(
                                    new ArrayList<>(menuMapper.findByRoleId(dto.getId())))));
        }
        return result;
    }

    @Override
    public RoleDTO findDtoById(Long id) {
        List<RoleDTO> result = assemble(List.of(findById(id)));
        return result.get(0);
    }

    private RoleDO findById(Long id) {
        String key = CacheKey.ROLE_ID + id;
        RoleDO role = redisUtils.get(key, RoleDO.class);
        if (role == null) {
            role = roleMapper.selectById(id);
            redisUtils.set(key, role, 1, TimeUnit.DAYS);
        }
        if (role == null) {
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND, "角色不存在");
        }
        return role;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(RoleCreateRequest request) {
        privilegeGuard.lockAdministration();
        checkLevel(request.getLevel());
        checkName(request.getName(), null);
        DataScopeEnum dataScope = validateDataScope(
                request.getDataScope(), request.getDepts(), null);
        RoleDO role = assembler.toEntity(request);
        role.setDataScope(dataScope.getValue());
        save(role);
        if (dataScope == DataScopeEnum.CUSTOM) {
            insertDepts(role.getId(), request.getDepts());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(RoleUpdateRequest request) {
        privilegeGuard.lockAdministration();
        privilegeGuard.changeRole(request.getId());
        RoleDO role = roleMapper.selectById(request.getId());
        checkLevel(role.getLevel());
        if (!java.util.Objects.equals(role.getEnabled(), request.getEnabled()))
            privilegeGuard.changeRoleState(role.getId());
        checkLevel(request.getLevel());
        checkName(request.getName(), request.getId());
        DataScopeEnum dataScope = validateDataScope(
                request.getDataScope(), request.getDepts(), role.getId());
        assembler.updateEntity(request, role);
        role.setDataScope(dataScope.getValue());
        updateById(role);
        // Custom data-scope rows represent the complete request state.
        roleDeptMapper.delete(
                Wrappers.<RoleDeptDO>lambdaQuery().eq(RoleDeptDO::getRoleId, role.getId()));
        if (dataScope == DataScopeEnum.CUSTOM) {
            insertDepts(role.getId(), request.getDepts());
        }
        delCaches(role.getId(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMenu(RoleMenuUpdateRequest request) {
        // 先持有管理锁并校验本次增删权限，再替换关联；防止绕过权限范围或修改自身角色。
        privilegeGuard.lockAdministration();
        Set<Long> menuIds = ids(request.getMenus());
        privilegeGuard.changeRolePermissions(request.getId(), menuIds);
        RoleDO role = roleMapper.selectById(request.getId());
        checkLevel(role.getLevel());
        List<UserDO> users = userMapper.findByRoleId(role.getId());
        // Menu assignments are replaced as one transaction to avoid partial authorization state.
        roleMenuMapper.delete(
                Wrappers.<RoleMenuDO>lambdaQuery().eq(RoleMenuDO::getRoleId, role.getId()));
        if (!menuIds.isEmpty()) {
            roleMenuMapper.insert(
                    menuIds.stream().map(id -> toRoleMenu(role.getId(), id)).toList());
        }
        // 权限变更提交后清理角色及受影响用户缓存，后续请求重新装配权限。
        delCaches(role.getId(), users);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(RoleDeleteRequest request) {
        privilegeGuard.lockAdministration();
        request.getIds().forEach(privilegeGuard::changeRole);
        for (Long id : request.getIds()) {
            checkLevel(roleMapper.selectById(id).getLevel());
        }
        if (userRoleMapper.selectCount(
                Wrappers.<UserRoleDO>lambdaQuery().in(UserRoleDO::getRoleId, request.getIds())) > 0)
            throw new BusinessException(CommonErrorCode.DATA_IN_USE, "所选角色存在用户关联，请解除关联后再试");
        request.getIds().forEach(id -> delCaches(id, null));
        removeBatchByIds(request.getIds());
        roleDeptMapper.delete(
                Wrappers.<RoleDeptDO>lambdaQuery().in(RoleDeptDO::getRoleId, request.getIds()));
        roleMenuMapper.delete(
                Wrappers.<RoleMenuDO>lambdaQuery().in(RoleMenuDO::getRoleId, request.getIds()));
    }

    private void checkName(String name, Long currentId) {
        RoleDO duplicate = roleMapper.selectOne(Wrappers.<RoleDO>lambdaQuery().eq(RoleDO::getName, name));
        if (duplicate != null && !duplicate.getId().equals(currentId))
            throw new BusinessException(CommonErrorCode.DATA_ALREADY_EXISTS, "角色名称已存在");
    }

    private void insertDepts(Long roleId, Set<IdReference> refs) {
        Set<Long> deptIds = ids(refs);
        if (!deptIds.isEmpty()) {
            roleDeptMapper.insert(
                    deptIds.stream().map(id -> toRoleDept(roleId, id)).toList());
        }
    }

    private RoleMenuDO toRoleMenu(Long roleId, Long menuId) {
        RoleMenuDO relation = new RoleMenuDO();
        relation.setRoleId(roleId);
        relation.setMenuId(menuId);
        return relation;
    }

    private RoleDeptDO toRoleDept(Long roleId, Long deptId) {
        RoleDeptDO relation = new RoleDeptDO();
        relation.setRoleId(roleId);
        relation.setDeptId(deptId);
        return relation;
    }

    private Set<Long> ids(Set<IdReference> refs) {
        return refs == null
                ? Collections.emptySet()
                : refs.stream().map(IdReference::getId).collect(Collectors.toSet());
    }

    private DataScopeEnum validateDataScope(
            String value, Set<IdReference> refs, Long existingRoleId) {
        DataScopeEnum configuredScope = DataScopeEnum.find(value);
        if (configuredScope == null) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "数据权限范围配置无效");
        }
        UserDataScope currentScope = dataService.getCurrentDataScope();
        if (configuredScope == DataScopeEnum.ALL && !currentScope.isAll()) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED, "不能授予超出自身的数据权限");
        }
        if (configuredScope == DataScopeEnum.CUSTOM) {
            Set<Long> deptIds = ids(refs);
            if (deptIds.isEmpty()) {
                throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "自定义数据权限必须选择部门");
            }
            if (deptMapper.selectBatchIds(deptIds).size() != deptIds.size()) {
                throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "数据权限包含不存在的部门");
            }
            dataService.checkDeptAccess(deptIds);
        }
        if (existingRoleId != null
                && (configuredScope == DataScopeEnum.DEPT
                || configuredScope == DataScopeEnum.DEPT_AND_CHILD)) {
            for (UserDO user : userMapper.findByRoleId(existingRoleId)) {
                checkConfiguredScope(configuredScope, existingRoleId, user.getDeptId());
            }
        }
        return configuredScope;
    }

    @Override
    public void checkDataScopes(Set<Long> roleIds, Long targetDeptId) {
        if (CollectionUtils.isEmpty(roleIds)) {
            return;
        }
        List<RoleDO> roles = roleMapper.selectBatchIds(roleIds);
        if (roles.size() != roleIds.size()) {
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND, "角色不存在");
        }
        for (RoleDO role : roles) {
            DataScopeEnum configuredScope = DataScopeEnum.find(role.getDataScope());
            if (configuredScope == null) {
                throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "数据权限范围配置无效");
            }
            checkConfiguredScope(configuredScope, role.getId(), targetDeptId);
        }
    }

    private void checkConfiguredScope(
            DataScopeEnum configuredScope, Long roleId, Long targetDeptId) {
        switch (configuredScope) {
            case ALL -> {
                if (!dataService.getCurrentDataScope().isAll()) {
                    throw new BusinessException(CommonErrorCode.ACCESS_DENIED, "不能授予超出自身的数据权限");
                }
            }
            case DEPT -> dataService.checkDeptAccess(targetDeptId);
            case DEPT_AND_CHILD -> {
                if (targetDeptId == null) {
                    dataService.checkDeptAccess((Long) null);
                } else {
                    dataService.checkDeptAccess(
                            dataService.expandDeptIds(List.of(targetDeptId)));
                }
            }
            case CUSTOM -> dataService.checkDeptAccess(
                    deptMapper.findByRoleId(roleId).stream().map(DeptDO::getId).toList());
            case SELF -> {
                // Self-only access does not grant access to another user's data.
            }
        }
    }

    @Override
    public List<RoleDO> findByUsersId(Long userId) {
        String key = CacheKey.ROLE_USER + userId;
        List<RoleDO> roles = redisUtils.getList(key, RoleDO.class);
        if (CollectionUtils.isEmpty(roles)) {
            roles = roleMapper.findByUserId(userId);
            redisUtils.set(key, roles, 1, TimeUnit.DAYS);
        }
        return roles;
    }

    @Override
    public Integer findByRoleIds(Set<Long> roleIds) {
        if (CollectionUtils.isEmpty(roleIds)) {
            return Integer.MAX_VALUE;
        }
        return roleMapper.selectBatchIds(roleIds).stream()
                .map(RoleDO::getLevel)
                .min(Integer::compareTo)
                .orElse(Integer.MAX_VALUE);
    }

    @Override
    public int getCurrentUserLevel() {
        return findByUsersId(SecurityUtils.getCurrentUserId()).stream()
                .map(RoleDO::getLevel)
                .min(Integer::compareTo)
                .orElse(Integer.MAX_VALUE);
    }

    private void checkLevel(Integer level) {
        int currentLevel = getCurrentUserLevel();
        if (level == null || level < currentLevel)
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED, "权限不足，无法操作该级别的角色");
    }

    /** Builds deduplicated menu permissions for the authentication principal. */
    @Override
    public List<AuthorityDTO> buildPermissions(UserLoginDTO user) {
        // Legacy API delegates to server-owned role permissions, never the isAdmin account flag.
        Set<String> permissions = new HashSet<>();
        for (RoleDO role : roleMapper.findByUserId(user.getId())) {
            if (!Boolean.TRUE.equals(role.getEnabled())) {
                continue;
            }
            menuMapper.findByRoleId(role.getId()).stream().filter(m -> Boolean.TRUE.equals(m.getEnabled()))
                    .map(MenuDO::getPermission).filter(StringUtils::isNotBlank).forEach(permissions::add);
        }
        return permissions.stream().map(AuthorityDTO::new).toList();
    }

    @Override
    public List<RoleDO> findByMenuId(Long menuId) {
        return roleMapper.findByMenuId(menuId);
    }

    private void delCaches(Long id, List<UserDO> users) {
        users = users == null ? userMapper.findByRoleId(id) : users;
        authInvalidator.publish(users.stream().map(UserDO::getId).toList(), List.of(id));
    }
}
