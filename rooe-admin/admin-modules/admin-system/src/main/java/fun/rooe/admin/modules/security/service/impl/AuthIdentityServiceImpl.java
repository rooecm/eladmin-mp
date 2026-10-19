package fun.rooe.admin.modules.security.service.impl;

import fun.rooe.admin.framework.security.model.UserSnapshot;
import fun.rooe.admin.framework.security.service.AuthIdentityService;
import fun.rooe.admin.modules.security.service.RolePermissionService;
import fun.rooe.admin.modules.security.service.UserCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 实现框架层认证数据接口，将用户快照与权限解析委托给业务服务，保持依赖方向。
 */
@Service
@RequiredArgsConstructor
public class AuthIdentityServiceImpl implements AuthIdentityService {
    private final UserCacheService users;
    private final RolePermissionService roles;

    @Override
    public UserSnapshot getUserForSession(long uid, long version) {
        return users.getUserForSession(uid, version);
    }

    @Override
    public List<GrantedAuthority> getAuthorities(List<Long> ids) {
        return roles.authorities(ids);
    }
}
