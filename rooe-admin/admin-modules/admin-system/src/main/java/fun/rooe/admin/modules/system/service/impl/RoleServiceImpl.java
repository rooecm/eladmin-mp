package fun.rooe.admin.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import fun.rooe.admin.constant.CacheKey;
import fun.rooe.admin.enums.DataScopeEnum;
import fun.rooe.admin.exception.BadRequestException;
import fun.rooe.admin.exception.EntityExistException;
import fun.rooe.admin.framework.utils.SecurityUtils;
import fun.rooe.admin.infrastructure.utils.RedisUtils;
import fun.rooe.admin.model.UserDataScope;
import fun.rooe.admin.modules.security.dto.AuthorityDTO;
import fun.rooe.admin.modules.security.service.UserCacheManager;
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
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements RoleService {
    private final RoleMapper roleMapper;
    private final RoleDeptMapper roleDeptMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final UserRoleMapper userRoleMapper;
    private final DeptMapper deptMapper;
    private final MenuMapper menuMapper;
    private final UserMapper userMapper;
    private final RedisUtils redisUtils;
    private final UserCacheManager userCacheManager;
    private final DataService dataService;
    private final RoleAssembler assembler;
    private final DeptAssembler deptAssembler;
    private final MenuAssembler menuAssembler;

    @Override
    public List<RoleDTO> queryAll() {
        return assemble(
                roleMapper.selectList(Wrappers.<Role>lambdaQuery().orderByAsc(Role::getLevel)));
    }

    @Override
    public PageResult<RoleDTO> queryAll(RoleQueryRequest request) {
        Page<Role> page = new Page<>(request.getPage(), request.getSize());
        roleMapper.selectPage(page, wrapper(request));
        return new PageResult<>(assemble(page.getRecords()), page.getTotal());
    }

    private LambdaQueryWrapper<Role> wrapper(RoleQueryRequest request) {
        LambdaQueryWrapper<Role> wrapper = Wrappers.lambdaQuery();
        wrapper.and(
                        StringUtils.isNotBlank(request.getBlurry()),
                        query ->
                                query.like(Role::getName, request.getBlurry())
                                        .or()
                                        .like(Role::getDescription, request.getBlurry()))
                .orderByAsc(Role::getLevel);
        if (request.getCreateTime() != null && request.getCreateTime().size() >= 2)
            wrapper.between(
                    Role::getCreateTime,
                    request.getCreateTime().get(0),
                    request.getCreateTime().get(1));
        return wrapper;
    }

    private List<RoleDTO> assemble(List<Role> roles) {
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

    private Role findById(Long id) {
        String key = CacheKey.ROLE_ID + id;
        Role role = redisUtils.get(key, Role.class);
        if (role == null) {
            role = roleMapper.selectById(id);
            redisUtils.set(key, role, 1, TimeUnit.DAYS);
        }
        return role;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(RoleCreateRequest request) {
        checkLevel(request.getLevel());
        checkName(request.getName(), null);
        DataScopeEnum dataScope = validateDataScope(
                request.getDataScope(), request.getDepts(), null);
        Role role = assembler.toEntity(request);
        role.setDataScope(dataScope.getValue());
        save(role);
        if (dataScope == DataScopeEnum.CUSTOM) {
            insertDepts(role.getId(), request.getDepts());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(RoleUpdateRequest request) {
        Role role = findById(request.getId());
        checkLevel(role.getLevel());
        checkLevel(request.getLevel());
        checkName(request.getName(), request.getId());
        DataScopeEnum dataScope = validateDataScope(
                request.getDataScope(), request.getDepts(), role.getId());
        assembler.updateEntity(request, role);
        role.setDataScope(dataScope.getValue());
        updateById(role);
        // Custom data-scope rows represent the complete request state.
        roleDeptMapper.delete(
                Wrappers.<RoleDept>lambdaQuery().eq(RoleDept::getRoleId, role.getId()));
        if (dataScope == DataScopeEnum.CUSTOM) {
            insertDepts(role.getId(), request.getDepts());
        }
        delCaches(role.getId(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMenu(RoleMenuUpdateRequest request) {
        Role role = findById(request.getId());
        checkLevel(role.getLevel());
        List<User> users = userMapper.findByRoleId(role.getId());
        // Menu assignments are replaced as one transaction to avoid partial authorization state.
        roleMenuMapper.delete(
                Wrappers.<RoleMenu>lambdaQuery().eq(RoleMenu::getRoleId, role.getId()));
        Set<Long> menuIds = ids(request.getMenus());
        if (!menuIds.isEmpty()) {
            roleMenuMapper.insert(
                    menuIds.stream().map(id -> toRoleMenu(role.getId(), id)).toList());
        }
        delCaches(role.getId(), users);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(RoleDeleteRequest request) {
        for (Long id : request.getIds()) checkLevel(findById(id).getLevel());
        if (userRoleMapper.selectCount(
                Wrappers.<UserRole>lambdaQuery().in(UserRole::getRoleId, request.getIds())) > 0)
            throw new BadRequestException("所选角色存在用户关联，请解除关联后再试");
        request.getIds().forEach(id -> delCaches(id, null));
        removeBatchByIds(request.getIds());
        roleDeptMapper.delete(
                Wrappers.<RoleDept>lambdaQuery().in(RoleDept::getRoleId, request.getIds()));
        roleMenuMapper.delete(
                Wrappers.<RoleMenu>lambdaQuery().in(RoleMenu::getRoleId, request.getIds()));
    }

    private void checkName(String name, Long currentId) {
        Role duplicate = roleMapper.selectOne(Wrappers.<Role>lambdaQuery().eq(Role::getName, name));
        if (duplicate != null && !duplicate.getId().equals(currentId))
            throw new EntityExistException(Role.class, "name", name);
    }

    private void insertDepts(Long roleId, Set<IdReference> refs) {
        Set<Long> deptIds = ids(refs);
        if (!deptIds.isEmpty()) {
            roleDeptMapper.insert(
                    deptIds.stream().map(id -> toRoleDept(roleId, id)).toList());
        }
    }

    private RoleMenu toRoleMenu(Long roleId, Long menuId) {
        RoleMenu relation = new RoleMenu();
        relation.setRoleId(roleId);
        relation.setMenuId(menuId);
        return relation;
    }

    private RoleDept toRoleDept(Long roleId, Long deptId) {
        RoleDept relation = new RoleDept();
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
            throw new BadRequestException("Invalid data scope: " + value);
        }
        UserDataScope currentScope = dataService.getCurrentDataScope();
        if (configuredScope == DataScopeEnum.ALL && !currentScope.isAll()) {
            throw new BadRequestException("Cannot grant a data scope broader than your own");
        }
        if (configuredScope == DataScopeEnum.CUSTOM) {
            Set<Long> deptIds = ids(refs);
            if (deptIds.isEmpty()) {
                throw new BadRequestException("Custom data scope departments cannot be empty");
            }
            if (deptMapper.selectBatchIds(deptIds).size() != deptIds.size()) {
                throw new BadRequestException("Data scope contains a department that does not exist");
            }
            dataService.checkDeptAccess(deptIds);
        }
        if (existingRoleId != null
                && (configuredScope == DataScopeEnum.DEPT
                || configuredScope == DataScopeEnum.DEPT_AND_CHILD)) {
            for (User user : userMapper.findByRoleId(existingRoleId)) {
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
        List<Role> roles = roleMapper.selectBatchIds(roleIds);
        if (roles.size() != roleIds.size()) {
            throw new BadRequestException("Role does not exist");
        }
        for (Role role : roles) {
            DataScopeEnum configuredScope = DataScopeEnum.find(role.getDataScope());
            if (configuredScope == null) {
                throw new BadRequestException(
                        "Invalid data scope configured for role: " + role.getName());
            }
            checkConfiguredScope(configuredScope, role.getId(), targetDeptId);
        }
    }

    private void checkConfiguredScope(
            DataScopeEnum configuredScope, Long roleId, Long targetDeptId) {
        switch (configuredScope) {
            case ALL -> {
                if (!dataService.getCurrentDataScope().isAll()) {
                    throw new BadRequestException(
                            "Cannot grant a data scope broader than your own");
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
                    deptMapper.findByRoleId(roleId).stream().map(Dept::getId).toList());
            case SELF -> {
                // Self-only access does not grant access to another user's data.
            }
        }
    }

    @Override
    public List<Role> findByUsersId(Long userId) {
        String key = CacheKey.ROLE_USER + userId;
        List<Role> roles = redisUtils.getList(key, Role.class);
        if (CollectionUtils.isEmpty(roles)) {
            roles = roleMapper.findByUserId(userId);
            redisUtils.set(key, roles, 1, TimeUnit.DAYS);
        }
        return roles;
    }

    @Override
    public Integer findByRoleIds(Set<Long> roleIds) {
        if (CollectionUtils.isEmpty(roleIds)) return Integer.MAX_VALUE;
        return roleMapper.selectBatchIds(roleIds).stream()
                .map(Role::getLevel)
                .min(Integer::compareTo)
                .orElse(Integer.MAX_VALUE);
    }

    @Override
    public int getCurrentUserLevel() {
        return findByUsersId(SecurityUtils.getCurrentUserId()).stream()
                .map(Role::getLevel)
                .min(Integer::compareTo)
                .orElse(Integer.MAX_VALUE);
    }

    private void checkLevel(Integer level) {
        int currentLevel = getCurrentUserLevel();
        if (level == null || level < currentLevel)
            throw new BadRequestException("权限不足，当前角色级别：" + currentLevel + "，操作角色级别：" + level);
    }

    /** Builds deduplicated menu permissions for the authentication principal. */
    @Override
    public List<AuthorityDTO> buildPermissions(UserLoginDTO user) {
        String key = CacheKey.ROLE_AUTH + user.getId();
        List<AuthorityDTO> cached = redisUtils.getList(key, AuthorityDTO.class);
        if (!CollectionUtils.isEmpty(cached)) return cached;
        if (Boolean.TRUE.equals(user.getIsAdmin())) return List.of(new AuthorityDTO("admin"));
        Set<String> permissions = new HashSet<>();
        for (Role role : roleMapper.findByUserId(user.getId())) {
            menuMapper.findByRoleId(role.getId()).stream()
                    .map(Menu::getPermission)
                    .filter(StringUtils::isNotBlank)
                    .forEach(permissions::add);
        }
        cached = permissions.stream().map(AuthorityDTO::new).toList();
        redisUtils.set(key, cached, 1, TimeUnit.HOURS);
        return cached;
    }

    @Override
    public List<Role> findByMenuId(Long menuId) {
        return roleMapper.findByMenuId(menuId);
    }

    private void delCaches(Long id, List<User> users) {
        users = CollectionUtils.isEmpty(users) ? userMapper.findByRoleId(id) : users;
        for (User user : users) userCacheManager.cleanUserCache(user.getUsername());
        Set<Long> userIds = users.stream().map(User::getId).collect(Collectors.toSet());
        redisUtils.delByKeys(CacheKey.DATA_USER, userIds);
        redisUtils.delByKeys(CacheKey.MENU_USER, userIds);
        redisUtils.delByKeys(CacheKey.ROLE_AUTH, userIds);
        redisUtils.delByKeys(CacheKey.ROLE_USER, userIds);
        redisUtils.del(CacheKey.ROLE_ID + id);
    }
}
