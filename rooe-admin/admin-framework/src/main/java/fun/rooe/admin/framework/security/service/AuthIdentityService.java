package fun.rooe.admin.framework.security.service;

import fun.rooe.admin.framework.security.model.UserSnapshot;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;

/**
 * 业务层实现的认证数据接口，使框架层无需依赖业务 Mapper 或持久化实体。
 */
public interface AuthIdentityService {
    UserSnapshot getUserForSession(long userId, long sessionAuthVersion);

    List<GrantedAuthority> getAuthorities(List<Long> roleIds);
}
