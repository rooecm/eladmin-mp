package fun.rooe.admin.modules.security.domain.dto;

import fun.rooe.admin.modules.system.domain.dto.UserDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

/**
 * 当前用户信息，作为 /api/auth/me 的响应载荷。
 */
@Getter
@AllArgsConstructor
public class CurrentUser {
    /**
     * 可向客户端返回的用户资料。
     */
    private final UserDTO user;
    /**
     * 当前请求实际拥有的权限码。
     */
    private final Set<String> roles;
    /**
     * 是否需要先修改密码。
     */
    private final boolean mustChangePassword;
}
