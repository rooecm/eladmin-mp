package fun.rooe.admin.modules.security.service;

import fun.rooe.admin.modules.security.domain.dto.RolePermissions;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface RolePermissionService {
    List<GrantedAuthority> authorities(List<Long> roleIds);

    Map<Long, RolePermissions> readFromDatabase(Collection<Long> roleIds);
}
