package fun.rooe.admin.modules.security.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.rooe.admin.framework.security.config.AuthProperties;
import fun.rooe.admin.infrastructure.security.RedisAuthCache;
import fun.rooe.admin.modules.security.domain.dto.RolePermissions;
import fun.rooe.admin.modules.security.service.AuthReadService;
import fun.rooe.admin.modules.security.service.RolePermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 按角色缓存权限，用一次 MGET 读取当前用户的全部角色；仅启用角色参与授权。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RolePermissionServiceImpl implements RolePermissionService {
    private final RedisAuthCache cache;
    private final AuthReadService reads;
    private final AuthProperties properties;
    private final ObjectMapper json;
    private final Object loadLock = new Object();

    @Override
    public List<GrantedAuthority> authorities(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, RolePermissions> roles = cached(ids);
        if (roles.size() < ids.size()) {
            synchronized (loadLock) {
                roles = cached(ids);
                List<Long> missing = new ArrayList<>(ids);
                missing.removeAll(roles.keySet());
                if (!missing.isEmpty()) {
                    // 在查询前固定缓存截止时间；只批量查询缺失角色，避免命中角色重复回源。
                    long deadline = cache.deadline(properties.getCache().getRolePermissionTtl());
                    Map<Long, RolePermissions> loaded = reads.roles(missing);
                    roles.putAll(loaded);
                    loaded.forEach((id, role) -> {
                        try {
                            cache.fill(RedisAuthCache.ROLE + id, json.writeValueAsString(role), deadline);
                        } catch (Exception e) {
                            cache.failure();
                            log.warn("Role cache fill unavailable");
                        }
                    });
                }
            }
        }
        Set<String> values = new TreeSet<>();
        // 权限码去重，角色编码另转为 ROLE_ 前缀的权限，用于识别超级管理员角色。
        for (RolePermissions role : roles.values()) {
            if (!role.isEnabled()) {
                continue;
            }
            values.addAll(role.getPermissions());
            if (role.getCode() != null && !role.getCode().isBlank()) {
                values.add("ROLE_" + role.getCode());
            }
        }
        return values.stream().<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();
    }

    private Map<Long, RolePermissions> cached(List<Long> ids) {
        Map<Long, RolePermissions> result = new HashMap<>();
        try {
            var values = cache.multiGet(ids.stream().map(id -> RedisAuthCache.ROLE + id).toList());
            if (values != null) for (int i = 0; i < ids.size(); i++) {
                if (values.get(i) != null) {
                    try {
                        result.put(ids.get(i), json.readValue(values.get(i), RolePermissions.class));
                    } catch (Exception e) {
                        cache.failure();
                    }
                }
            }
        } catch (RuntimeException e) {
            cache.failure();
        }
        return result;
    }

    @Override
    public Map<Long, RolePermissions> readFromDatabase(Collection<Long> ids) {
        return reads.roles(ids);
    }
}
