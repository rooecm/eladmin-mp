package fun.rooe.admin.modules.security.domain.entity;

import lombok.Data;

@Data
public class RolePermissionRow {
    private Long roleId;
    private String roleCode;
    private Boolean enabled;
    private String permission;
}
