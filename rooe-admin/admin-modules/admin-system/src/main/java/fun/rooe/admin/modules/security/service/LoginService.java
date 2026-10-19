package fun.rooe.admin.modules.security.service;

import fun.rooe.admin.modules.security.domain.dto.LoginCredentialSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 仅用于账号密码登录，直接查库获取校验快照，不使用请求认证缓存。
 */
@Service
@RequiredArgsConstructor
public class LoginService implements UserDetailsService {
    private final AuthReadService reads;

    public static String canonical(String username) {
        // 登录查询统一去除首尾空格并转为小写，与用户创建和更新的账号规范保持一致。
        return username.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public LoginCredentialSnapshot loadUserByUsername(String username) {
        var user = reads.login(canonical(username));
        if (user == null) {
            throw new UsernameNotFoundException("Invalid credentials");
        }
        return new LoginCredentialSnapshot(user);
    }
}
