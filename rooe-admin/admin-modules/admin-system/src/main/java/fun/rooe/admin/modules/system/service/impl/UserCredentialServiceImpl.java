package fun.rooe.admin.modules.system.service.impl;

import fun.rooe.admin.modules.system.mapper.UserMapper;
import fun.rooe.admin.modules.system.service.UserCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Keeps password hashes on the database-backed authentication path and out of user caches.
 */
@Service
@RequiredArgsConstructor
public class UserCredentialServiceImpl implements UserCredentialService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public boolean matches(String username, String rawPassword) {
        String passwordHash = userMapper.findPasswordHashByUsername(username);
        return passwordHash != null && passwordEncoder.matches(rawPassword, passwordHash);
    }

    @Override
    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
}
