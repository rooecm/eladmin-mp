package fun.rooe.admin.modules.security.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fun.rooe.admin.modules.system.domain.entity.UserDO;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

/**
 * 登录专用的密码校验快照，不进入缓存、日志或接口响应；认证后擦除密码哈希。
 */
public class LoginCredentialSnapshot implements UserDetails, CredentialsContainer {
    private final UserDO snapshot;

    public LoginCredentialSnapshot(UserDO snapshot) {
        this.snapshot = snapshot;
    }

    @JsonIgnore
    public UserDO snapshot() {
        return snapshot;
    }

    @Override
    public String getUsername() {
        return snapshot.getUsername();
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        return snapshot.getPassword();
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        return List.of();
    }

    // 先让认证提供者验证密码，再由业务层检查真实启用状态，避免未验证密码就暴露账号状态。
    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void eraseCredentials() {
        snapshot.setPassword(null);
    }
}
