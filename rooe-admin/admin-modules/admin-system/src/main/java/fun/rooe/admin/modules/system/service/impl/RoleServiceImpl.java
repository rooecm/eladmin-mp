package fun.rooe.admin.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import fun.rooe.admin.constant.CacheKey;
import fun.rooe.admin.exception.BadRequestException;
import fun.rooe.admin.exception.EntityExistException;
import fun.rooe.admin.framework.utils.SecurityUtils;
import fun.rooe.admin.infrastructure.utils.RedisUtils;
import fun.rooe.admin.modules.security.dto.AuthorityDto;
import fun.rooe.admin.modules.security.service.UserCacheManager;
import fun.rooe.admin.modules.system.converter.DeptAssembler;
import fun.rooe.admin.modules.system.converter.MenuAssembler;
import fun.rooe.admin.modules.system.converter.RoleAssembler;
import fun.rooe.admin.modules.system.domain.dto.RoleDto;
import fun.rooe.admin.modules.system.domain.dto.UserLoginDto;
import fun.rooe.admin.modules.system.domain.entity.*;
import fun.rooe.admin.modules.system.domain.request.*;
import fun.rooe.admin.modules.system.mapper.*;
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
    private final RoleAssembler assembler;
    private final DeptAssembler deptAssembler;
    private final MenuAssembler menuAssembler;

    @Override
    public List<RoleDto> queryAll() {
        return assemble(
                roleMapper.selectList(Wrappers.<Role>lambdaQuery().orderByAsc(Role::getLevel)));
    }

    @Override
    public PageResult<RoleDto> queryAll(RoleQueryRequest request) {
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

    private List<RoleDto> assemble(List<Role> roles) {
        List<RoleDto> result = assembler.toDtos(roles);
        for (RoleDto dto : result) {
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
    public RoleDto findDtoById(Long id) {
        List<RoleDto> result = assemble(List.of(findById(id)));
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
        Role role = assembler.toEntity(request);
        save(role);
        insertDepts(role.getId(), request.getDepts());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(RoleUpdateRequest request) {
        Role role = findById(request.getId());
        checkLevel(role.getLevel());
        checkLevel(request.getLevel());
        checkName(request.getName(), request.getId());
        assembler.updateEntity(request, role);
        updateById(role);
        // Custom data-scope rows represent the complete request state.
        roleDeptMapper.delete(
                Wrappers.<RoleDept>lambdaQuery().eq(RoleDept::getRoleId, role.getId()));
        insertDepts(role.getId(), request.getDepts());
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
                    menuIds.stream().map(id -> new RoleMenu(role.getId(), id)).toList());
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
                    deptIds.stream().map(id -> new RoleDept(roleId, id)).toList());
        }
    }

    private Set<Long> ids(Set<IdReference> refs) {
        return refs == null
                ? Collections.emptySet()
                : refs.stream().map(IdReference::getId).collect(Collectors.toSet());
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
    public List<AuthorityDto> buildPermissions(UserLoginDto user) {
        String key = CacheKey.ROLE_AUTH + user.getId();
        List<AuthorityDto> cached = redisUtils.getList(key, AuthorityDto.class);
        if (!CollectionUtils.isEmpty(cached)) return cached;
        if (Boolean.TRUE.equals(user.getIsAdmin())) return List.of(new AuthorityDto("admin"));
        Set<String> permissions = new HashSet<>();
        for (Role role : roleMapper.findByUserId(user.getId())) {
            menuMapper.findByRoleId(role.getId()).stream()
                    .map(Menu::getPermission)
                    .filter(StringUtils::isNotBlank)
                    .forEach(permissions::add);
        }
        cached = permissions.stream().map(AuthorityDto::new).toList();
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
