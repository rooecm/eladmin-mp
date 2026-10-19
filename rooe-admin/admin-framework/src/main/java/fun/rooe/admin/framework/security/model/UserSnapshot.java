package fun.rooe.admin.framework.security.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 框架层使用的不含密码的用户快照；数据库实体保留在 admin-system 业务层。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSnapshot {
    /**
     * User ID.
     */
    private long id;
    /**
     * Username.
     */
    private String username;
    /**
     * Nickname.
     */
    private String nickName;
    /**
     * Whether the account is enabled.
     */
    private boolean enabled;
    /**
     * Whether the user must change password before further access.
     */
    private boolean mustChangePassword;
    /**
     * Monotonic authentication version used for session validation.
     */
    private long authVersion;
    /**
     * Role IDs used to load permissions.
     */
    private List<Long> roleIds;
}
