package fun.rooe.admin.modules.security.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Role-level permission snapshot cached in Redis and used for authorization checks.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RolePermissions {
    /**
     * Role code, also mapped to the ROLE_ prefixed authority to recognize the super admin role.
     */
    private String code;
    /**
     * Whether the role is enabled; only enabled roles participate in authorization.
     */
    private boolean enabled;
    /**
     * Permission codes granted by this role.
     */
    private Set<String> permissions;
}
