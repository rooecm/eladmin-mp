package fun.rooe.admin.framework.security.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 本次请求的认证主体，权限随请求装配，不持有原始 Token 或密码。
 */
@Getter
@AllArgsConstructor
public class LoginUser implements UserDetails {
    /**
     * User ID.
     */
    private final long id;
    /**
     * Username.
     */
    private final String username;
    /**
     * Nickname.
     */
    private final String nickName;
    /**
     * Server-side session identifier.
     */
    private final String sessionId;
    /**
     * Whether the user must change password.
     */
    private final boolean mustChangePassword;
    /**
     * Granted authorities assembled per request.
     */
    private final List<GrantedAuthority> authorities;

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        return null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }
}