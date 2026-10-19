package fun.rooe.admin.framework.security;

import fun.rooe.admin.framework.config.security.config.AuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 供 @PreAuthorize 使用，仅认可本次认证加载的权限码与启用的超级管理员角色。
 */
@Component("perm")
@RequiredArgsConstructor
public class PermissionChecker {
    private final AuthProperties properties;

    public boolean has(String permission) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)
                && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(permission)
                || a.getAuthority().equals("ROLE_" + properties.getSuperAdminRoleCode()));
    }
}
