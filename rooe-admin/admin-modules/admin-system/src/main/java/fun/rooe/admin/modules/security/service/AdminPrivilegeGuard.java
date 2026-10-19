package fun.rooe.admin.modules.security.service;

import fun.rooe.admin.error.AuthErrorCode;
import fun.rooe.admin.framework.security.AuthFailure;
import fun.rooe.admin.framework.security.AuthTelemetry;
import fun.rooe.admin.framework.security.config.AuthProperties;
import fun.rooe.admin.framework.utils.SecurityUtils;
import fun.rooe.admin.modules.security.domain.dto.RolePermissions;
import fun.rooe.admin.modules.security.mapper.AuthMapper;
import fun.rooe.admin.modules.system.mapper.MenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理写操作的防提权校验，在业务事务内直接查库，不依赖可能过期的权限缓存。
 * 调用方先执行 lockAdministration，再校验和写入；需要用户行锁时始终先锁超级管理员角色。
 */
@Service
@RequiredArgsConstructor
public class AdminPrivilegeGuard {
    private final AuthMapper mapper;
    private final MenuMapper menus;
    private final AuthProperties properties;
    private final AuthTelemetry telemetry;

    public void lockAdministration() {
        // 所有管理写操作共用角色行锁，避免并发请求同时通过“最后一个超级管理员”检查。
        AuthCacheInvalidator.requireTransaction();
        if (mapper.lockSuperRole(properties.getSuperAdminRoleCode()) == null)
            throw new AuthFailure(403, AuthErrorCode.PROTECTED_RESOURCE);
    }

    private Map<Long, RolePermissions> roles(Collection<Long> ids) {
        Map<Long, RolePermissions> result = new HashMap<>();
        if (!ids.isEmpty()) for (var row : mapper.rolePermissions(ids)) {
            var role = result.computeIfAbsent(row.getRoleId(), id -> new RolePermissions(row.getRoleCode(),
                    Boolean.TRUE.equals(row.getEnabled()), new HashSet<>()));
            if (row.getPermission() != null && !row.getPermission().isBlank()) {
                role.getPermissions().add(row.getPermission());
            }
        }
        return result;
    }

    private boolean isSuper(Map<Long, RolePermissions> roles) {
        return roles.values().stream().anyMatch(r -> r.isEnabled() && properties.getSuperAdminRoleCode().equals(r.getCode()));
    }

    private Set<String> permissions(Map<Long, RolePermissions> roles) {
        return roles.values().stream().filter(RolePermissions::isEnabled).flatMap(r -> r.getPermissions().stream()).collect(Collectors.toSet());
    }

    /**
     * 校验用户角色变更：禁止修改自身角色、越权授权及移除最后一个启用的超级管理员。
     */
    public void assignRoles(Long targetId, Set<Long> newRoles) {
        long actor = SecurityUtils.getCurrentUserId();
        var actorRoles = roles(mapper.roleIds(actor));
        Set<Long> old = targetId == null ? Set.of() : new HashSet<>(mapper.roleIds(targetId));
        if (old.equals(newRoles)) {
            return;
        }
        if (Objects.equals(targetId, actor)) {
            deny("R3");
        }
        var requested = roles(newRoles);
        if (requested.size() != newRoles.size()) {
            deny("R1");
        }
        boolean superActor = isSuper(actorRoles);
        boolean superTarget = isSuper(roles(old));
        // 对称差集只包含本次新增或移除的角色，用于识别是否触及超级管理员角色。
        Set<Long> changed = new HashSet<>(old);
        changed.addAll(newRoles);
        Set<Long> common = new HashSet<>(old);
        common.retainAll(newRoles);
        changed.removeAll(common);
        if (roles(changed).values().stream().anyMatch(r -> properties.getSuperAdminRoleCode().equals(r.getCode())) && !superActor) {
            deny("R4");
        }
        if (superTarget && !superActor) {
            deny("R5");
        }
        if (!superActor && !permissions(actorRoles).containsAll(permissions(requested))) {
            deny("R1");
        }
        if (superTarget && !isSuper(requested)) {
            ensureAnotherSuper(targetId);
        }
        telemetry.audit("USER_ROLES_CHANGED", targetId, "assigned");
    }

    /**
     * 删除、禁用以及敏感用户修改共用入口；自身和超级管理员另有保护规则。
     */
    public void changeUser(long targetId, boolean disablingOrDeleting) {
        long actor = SecurityUtils.getCurrentUserId();
        if (disablingOrDeleting && targetId == actor) {
            deny("R6");
        }
        if (isSuper(roles(mapper.roleIds(targetId)))) {
            if (!isSuper(roles(mapper.roleIds(actor)))) {
                deny("R5");
            }
            if (disablingOrDeleting) {
                ensureAnotherSuper(targetId);
            }
        }
    }

    private void ensureAnotherSuper(long targetId) {
        var target = mapper.findUser(targetId);
        if (target != null && Boolean.TRUE.equals(target.getEnabled())
                && mapper.enabledSuperUsers(properties.getSuperAdminRoleCode()) <= 1) deny("R6");
    }

    public void changeRole(long roleId) {
        var role = roles(List.of(roleId)).get(roleId);
        if (role != null && properties.getSuperAdminRoleCode().equals(role.getCode())) {
            deny("R4");
        }
    }

    /**
     * 同时校验新增与移除的菜单权限，防止通过修改角色间接改变自身权限。
     */
    public void changeRolePermissions(long roleId, Set<Long> newMenuIds) {
        changeRole(roleId);
        long actor = SecurityUtils.getCurrentUserId();
        if (mapper.roleIds(actor).contains(roleId)) {
            deny("R3");
        }
        var actorRoles = roles(mapper.roleIds(actor));
        if (isSuper(actorRoles)) {
            return;
        }
        Set<Long> old = menus.findByRoleId(roleId).stream().map(m -> m.getId()).collect(Collectors.toSet());
        Set<Long> changed = new HashSet<>(old);
        changed.addAll(newMenuIds);
        Set<Long> same = new HashSet<>(old);
        same.retainAll(newMenuIds);
        changed.removeAll(same);
        if (changed.isEmpty()) {
            return;
        }
        Set<String> allowed = permissions(actorRoles);
        var entries = menus.selectBatchIds(changed);
        if (entries.size() != changed.size()) {
            deny("R2");
        }
        for (var menu : entries)
            if (menu.getPermission() != null && !menu.getPermission().isBlank()
                    && !allowed.contains(menu.getPermission())) deny("R2");
        telemetry.audit("ROLE_PERMISSIONS_CHANGED", actor, "assigned");
    }

    public void changeRoleState(long roleId) {
        long actor = SecurityUtils.getCurrentUserId();
        var actorRoles = roles(mapper.roleIds(actor));
        if (mapper.roleIds(actor).contains(roleId)) {
            deny("R3");
        }
        var target = roles(List.of(roleId)).get(roleId);
        if (target != null && !isSuper(actorRoles)
                && !permissions(actorRoles).containsAll(target.getPermissions())) deny("R2");
    }

    /**
     * 修改菜单权限码也必须校验关联角色，避免绕过角色授权入口的防提权检查。
     */
    public void changePermission(Collection<Long> roleIds, String oldCode, String newCode) {
        long actor = SecurityUtils.getCurrentUserId();
        var actorRoles = roles(mapper.roleIds(actor));
        if (isSuper(actorRoles)) {
            return;
        }
        if (roleIds.stream().anyMatch(mapper.roleIds(actor)::contains)) {
            deny("R3");
        }
        var allowed = permissions(actorRoles);
        for (String code : List.of(oldCode == null ? "" : oldCode, newCode == null ? "" : newCode))
            if (!code.isBlank() && !allowed.contains(code)) {
                deny("R2");
            }
    }

    private void deny(String rule) {
        telemetry.count("auth_privilege_denied_total", "rule", rule);
        telemetry.audit("PRIVILEGE_DENIED", SecurityUtils.getCurrentUserId(), rule);
        throw new AuthFailure(403, Set.of("R1", "R2", "R3").contains(rule)
                ? AuthErrorCode.PRIVILEGE_ESCALATION : AuthErrorCode.PROTECTED_RESOURCE);
    }
}
