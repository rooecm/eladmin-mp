package fun.rooe.admin.modules.security.service;

import fun.rooe.admin.framework.security.model.AuthenticatedUser;
import fun.rooe.admin.modules.security.domain.dto.RolePermissions;
import fun.rooe.admin.modules.security.mapper.AuthMapper;
import fun.rooe.admin.modules.system.domain.entity.UserDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 认证数据通过独立的主库只读事务查询，使用 READ_COMMITTED 避免复用调用方的旧快照。
 * 缓存回源与登录校验共用此入口，数据库实体不会直接传入框架层。
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED, readOnly = true)
public class AuthReadService {
    private final AuthMapper mapper;

    public UserDO login(String canonicalUsername) {
        return mapper.findForLogin(canonicalUsername);
    }

    public AuthenticatedUser user(long id) {
        UserDO user = mapper.findUser(id);
        return user == null ? null : new AuthenticatedUser(user.getId(), user.getUsername(), user.getNickName(),
                Boolean.TRUE.equals(user.getEnabled()), Boolean.TRUE.equals(user.getMustChangePassword()),
                user.getAuthVersion(), List.copyOf(mapper.roleIds(id)));
    }

    public Map<Long, RolePermissions> roles(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, RolePermissions> result = new LinkedHashMap<>();
        for (var row : mapper.rolePermissions(ids)) {
            var role = result.computeIfAbsent(row.getRoleId(), id -> new RolePermissions(
                    row.getRoleCode(), Boolean.TRUE.equals(row.getEnabled()), new HashSet<>()));
            if (row.getPermission() != null && !row.getPermission().isBlank())
                role.getPermissions().add(row.getPermission());
        }
        return result;
    }
}
