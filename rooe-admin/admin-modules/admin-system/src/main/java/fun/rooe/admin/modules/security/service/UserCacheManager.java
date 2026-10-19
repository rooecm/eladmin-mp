package fun.rooe.admin.modules.security.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import fun.rooe.admin.modules.system.domain.entity.UserDO;
import fun.rooe.admin.modules.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Compatibility entry point for existing business cache invalidation. Never caches principals.
 */
@Component
@RequiredArgsConstructor
public class UserCacheManager {
    private final AuthCacheInvalidator invalidator;
    private final UserMapper users;

    public void cleanUserCache(String username) {
        var user = users.selectOne(Wrappers.<UserDO>lambdaQuery().eq(UserDO::getUsername, username));
        if (user != null) {
            invalidator.users(List.of(user.getId())); }
    }
    public void cleanAllUserCaches() {
        invalidator.users(users.selectList(Wrappers.<UserDO>lambdaQuery().select(UserDO::getId))
                .stream().map(UserDO::getId).toList());
    }
}
