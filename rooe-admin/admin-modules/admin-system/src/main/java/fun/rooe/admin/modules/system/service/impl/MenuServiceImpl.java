package fun.rooe.admin.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import fun.rooe.admin.constant.CacheKey;
import fun.rooe.admin.error.CommonErrorCode;
import fun.rooe.admin.exception.BusinessException;
import fun.rooe.admin.framework.utils.SecurityUtils;
import fun.rooe.admin.infrastructure.utils.RedisUtils;
import fun.rooe.admin.modules.system.converter.MenuAssembler;
import fun.rooe.admin.modules.system.domain.dto.MenuDTO;
import fun.rooe.admin.modules.system.domain.dto.MenuRouterDTO;
import fun.rooe.admin.modules.system.domain.entity.MenuDO;
import fun.rooe.admin.modules.system.domain.entity.RoleDO;
import fun.rooe.admin.modules.system.domain.entity.RoleMenuDO;
import fun.rooe.admin.modules.system.domain.entity.UserDO;
import fun.rooe.admin.modules.system.domain.request.*;
import fun.rooe.admin.modules.system.mapper.MenuMapper;
import fun.rooe.admin.modules.system.mapper.RoleMenuMapper;
import fun.rooe.admin.modules.system.mapper.UserMapper;
import fun.rooe.admin.modules.system.service.MenuService;
import fun.rooe.admin.modules.system.service.RoleService;
import fun.rooe.admin.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Coordinates menu persistence, tree traversal, router assembly, and menu caches.
 */
@Service
@RequiredArgsConstructor
public class MenuServiceImpl extends ServiceImpl<MenuMapper, MenuDO> implements MenuService {
    private static final String HTTP_PRE = "http://";
    private static final String HTTPS_PRE = "https://";
    private final MenuMapper menuMapper;
    private final fun.rooe.admin.modules.security.service.AuthCacheInvalidator authInvalidator;
    private final fun.rooe.admin.modules.security.service.AdminPrivilegeGuard privilegeGuard;
    private final RoleMenuMapper roleMenuMapper;
    private final UserMapper userMapper;
    private final RoleService roleService;
    private final RedisUtils redisUtils;
    private final MenuAssembler assembler;

    @Override
    public List<MenuDTO> queryAll(MenuQueryRequest request) {
        boolean noExplicitCondition =
                StringUtils.isBlank(request.getBlurry())
                        && request.getPid() == null
                        && request.getCreateTime() == null;
        LambdaQueryWrapper<MenuDO> wrapper = Wrappers.lambdaQuery();
        wrapper.and(
                        StringUtils.isNotBlank(request.getBlurry()),
                        query ->
                                query.like(MenuDO::getTitle, request.getBlurry())
                                        .or()
                                        .like(MenuDO::getComponentName, request.getBlurry())
                                        .or()
                                        .like(MenuDO::getPermission, request.getBlurry()))
                .isNull(
                        noExplicitCondition || Boolean.TRUE.equals(request.getPidIsNull()),
                        MenuDO::getPid)
                .eq(request.getPid() != null, MenuDO::getPid, request.getPid())
                .orderByAsc(MenuDO::getMenuSort);
        if (request.getCreateTime() != null && request.getCreateTime().size() >= 2) {
            wrapper.between(
                    MenuDO::getCreateTime,
                    request.getCreateTime().get(0),
                    request.getCreateTime().get(1));
        }
        return assembler.toDtos(menuMapper.selectList(wrapper));
    }

    @Override
    public List<MenuRouterDTO> buildCurrentUserMenus() {
        List<MenuDO> menus = findByUser(SecurityUtils.getCurrentUserId());
        return assembler.toRouterDtos(assembler.toTree(assembler.toDtos(menus.stream()
                .filter(menu -> Boolean.TRUE.equals(menu.getEnabled())).toList())));
    }

    private MenuDO findById(long id) {
        String key = CacheKey.MENU_ID + id;
        MenuDO menu = redisUtils.get(key, MenuDO.class);
        if (menu == null) {
            menu = getById(id);
            redisUtils.set(key, menu, 1, TimeUnit.DAYS);
        }
        return menu;
    }

    private List<MenuDO> findByUser(Long userId) {
        String key = CacheKey.MENU_USER + userId;
        List<MenuDO> menus = redisUtils.getList(key, MenuDO.class);
        if (CollectionUtils.isEmpty(menus)) {
            Set<Long> roleIds =
                    roleService.findByUsersId(userId).stream()
                            .filter(r -> Boolean.TRUE.equals(r.getEnabled())).map(RoleDO::getId)
                            .collect(Collectors.toSet());
            menus =
                    roleIds.isEmpty()
                            ? new ArrayList<>()
                            : new ArrayList<>(menuMapper.findByRoleIdsAndTypeNot(roleIds, 2));
            redisUtils.set(key, menus, 1, TimeUnit.DAYS);
        }
        return menus;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(MenuCreateRequest request) {
        // 菜单权限码同样属于授权资源，必须经过管理锁与权限范围检查。
        privilegeGuard.lockAdministration();
        privilegeGuard.changePermission(List.of(), null, request.getPermission());
        validateUnique(request.getTitle(), request.getComponentName(), null);
        validateFrame(request.getIFrame(), request.getPath());
        MenuDO menu = assembler.toEntity(request);
        if (Long.valueOf(0L).equals(menu.getPid())) {
            menu.setPid(null);
        }
        menu.setSubCount(0);
        save(menu);
        updateSubCnt(menu.getPid());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(MenuUpdateRequest request) {
        privilegeGuard.lockAdministration();
        if (request.getId().equals(request.getPid())) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "上级菜单不能为自己");
        }
        validateUnique(request.getTitle(), request.getComponentName(), request.getId());
        validateFrame(request.getIFrame(), request.getPath());
        MenuDO menu = getById(request.getId());
        if (!Objects.equals(menu.getPermission(), request.getPermission())
                || !Objects.equals(menu.getEnabled(), request.getEnabled()))
            privilegeGuard.changePermission(roleService.findByMenuId(menu.getId()).stream().map(RoleDO::getId).toList(),
                    menu.getPermission(), request.getPermission());
        Long oldPid = menu.getPid();
        assembler.updateEntity(request, menu);
        if (Long.valueOf(0L).equals(menu.getPid())) {
            menu.setPid(null);
        }
        updateById(menu);
        updateSubCnt(oldPid);
        updateSubCnt(menu.getPid());
        delCaches(menu.getId());
    }

    private void validateUnique(String title, String componentName, Long currentId) {
        MenuDO duplicate =
                menuMapper.selectOne(Wrappers.<MenuDO>lambdaQuery().eq(MenuDO::getTitle, title));
        if (duplicate != null && !duplicate.getId().equals(currentId))
            throw new BusinessException(CommonErrorCode.DATA_ALREADY_EXISTS, "菜单标题已存在");
        if (StringUtils.isNotBlank(componentName)) {
            duplicate =
                    menuMapper.selectOne(
                            Wrappers.<MenuDO>lambdaQuery().eq(MenuDO::getComponentName, componentName));
            if (duplicate != null && !duplicate.getId().equals(currentId))
                throw new BusinessException(CommonErrorCode.DATA_ALREADY_EXISTS, "菜单组件名称已存在");
        }
    }

    private void validateFrame(Boolean frame, String path) {
        if (Boolean.TRUE.equals(frame)
                && (path == null
                || !(path.toLowerCase().startsWith(HTTP_PRE)
                || path.toLowerCase().startsWith(HTTPS_PRE)))) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "外链必须以 http:// 或 https:// 开头");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(MenuDeleteRequest request) {
        // 先收集本批次及子菜单并逐项校验，全部删除共享事务，禁止部分成功。
        privilegeGuard.lockAdministration();
        Map<Long, MenuDO> byId = new LinkedHashMap<>();
        request.getIds().forEach(id -> collect(getById(id), byId));
        for (MenuDO menu : byId.values()) {
            privilegeGuard.changePermission(roleService.findByMenuId(menu.getId()).stream().map(RoleDO::getId).toList(),
                    menu.getPermission(), null);
            delCaches(menu.getId());
            roleMenuMapper.delete(
                    Wrappers.<RoleMenuDO>lambdaQuery().eq(RoleMenuDO::getMenuId, menu.getId()));
            menuMapper.deleteById(menu.getId());
            updateSubCnt(menu.getPid());
        }
    }

    /** Collects a menu subtree once per ID so overlapping delete selections remain idempotent. */
    private void collect(MenuDO menu, Map<Long, MenuDO> byId) {
        if (menu == null || byId.putIfAbsent(menu.getId(), menu) != null) {
            return;
        }
        getMenuEntities(menu.getId()).forEach(child -> collect(child, byId));
    }

    @Override
    public List<MenuDTO> getMenus(Long pid) {
        return assembler.toDtos(getMenuEntities(pid));
    }

    private List<MenuDO> getMenuEntities(Long pid) {
        LambdaQueryWrapper<MenuDO> wrapper = Wrappers.lambdaQuery();
        if (pid != null && !pid.equals(0L)) {
            wrapper.eq(MenuDO::getPid, pid);
        } else {
            wrapper.isNull(MenuDO::getPid);
        }
        return menuMapper.selectList(wrapper.orderByAsc(MenuDO::getMenuSort));
    }

    @Override
    public Set<Long> getChildIds(Long id) {
        Map<Long, MenuDO> byId = new LinkedHashMap<>();
        collect(findById(id), byId);
        return byId.keySet();
    }

    /**
     * Returns complete ancestor branches for tree selectors. When selected nodes are excluded,
     * parent child counts are adjusted so the frontend can render the remaining branch correctly.
     */
    @Override
    public List<MenuDTO> getSuperior(MenuSuperiorRequest request) {
        Map<Long, MenuDO> byId = new LinkedHashMap<>();
        Set<Long> adjustedParents = new HashSet<>();
        for (Long id : request.getIds()) {
            MenuDO menu = findById(id);
            if (menu == null) {
                continue;
            }
            List<MenuDO> branch = getSuperiorEntities(menu, new ArrayList<>());
            if (request.isExclude()) {
                if (menu.getPid() != null) {
                    adjustedParents.add(menu.getPid());
                }
                branch =
                        branch.stream()
                                .filter(item -> !request.getIds().contains(item.getId()))
                                .toList();
            }
            branch.forEach(item -> byId.putIfAbsent(item.getId(), item));
        }
        List<MenuDTO> dtos = assembler.toDtos(new ArrayList<>(byId.values()));
        dtos.stream()
                .filter(item -> adjustedParents.contains(item.getId()))
                .forEach(
                        item ->
                                item.setSubCount(
                                        Math.max(
                                                0,
                                                Optional.ofNullable(item.getSubCount()).orElse(0)
                                                        - 1)));
        return assembler.toTree(dtos);
    }

    private List<MenuDO> getSuperiorEntities(MenuDO menu, List<MenuDO> result) {
        if (menu.getPid() == null) {
            result.addAll(getMenuEntities(null));
            return result;
        }
        result.addAll(getMenuEntities(menu.getPid()));
        return getSuperiorEntities(findById(menu.getPid()), result);
    }

    private void updateSubCnt(Long id) {
        if (id != null) {
            long count = menuMapper.selectCount(
                    Wrappers.<MenuDO>lambdaQuery().eq(MenuDO::getPid, id));
            menuMapper.update(Wrappers.<MenuDO>lambdaUpdate()
                    .eq(MenuDO::getId, id)
                    .set(MenuDO::getSubCount, Math.toIntExact(count)));
        }
    }

    private void delCaches(Long id) {
        authInvalidator.keys(List.of(CacheKey.MENU_ID + id));
        var users = userMapper.findByMenuId(id);
        var roles = roleService.findByMenuId(id);
        authInvalidator.publish(users.stream().map(UserDO::getId).toList(), roles.stream().map(RoleDO::getId).toList());
    }
}
