package fun.rooe.admin.modules.security.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import fun.rooe.admin.framework.security.config.AuthProperties;
import fun.rooe.admin.modules.security.mapper.AuthMapper;
import fun.rooe.admin.modules.system.domain.entity.UserDO;
import fun.rooe.admin.modules.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 启动时恢复被禁用的历史演示管理员，使用配置中的初始密码并要求首次登录后改密。
 * 每次启动都会检查；仅匹配历史密码且仍被禁用的 admin 会被处理，不创建新账号。
 */
@Component
@RequiredArgsConstructor
public class InitialAdminInitializer implements ApplicationRunner {
    private final AuthProperties properties;
    private final AuthMapper auth;
    private final UserMapper users;
    private final PasswordEncoder passwords;
    private final AuthVersionService versions;
    private final AuthCacheInvalidator invalidator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        String initial = properties.getInitialPassword();
        // 未配置 AUTH_INITIAL_PASSWORD 时跳过，避免自动启用默认管理员。
        if (initial == null || initial.isBlank()) {
            return;
        }
        // 与管理写操作保持相同锁顺序：先锁超级管理员角色，再锁用户行。
        auth.lockSuperRole(properties.getSuperAdminRoleCode());
        UserDO admin = users.selectOne(Wrappers.<UserDO>lambdaQuery().eq(UserDO::getUsername, "admin").last("FOR UPDATE"));
        // 只识别已知演示密码；已启用或自行设置过密码的管理员不会被重置。
        if (admin == null || Boolean.TRUE.equals(admin.getEnabled()) || !
                "$2a$10$Egp1/gvFlt7zhlXVfEFw4OfWQCGPw0ClmMcc6FjTnvXNRVf9zdMRa".equals(admin.getPassword())) return;
        users.update(Wrappers.<UserDO>lambdaUpdate().eq(UserDO::getId, admin.getId())
                .set(UserDO::getPassword, passwords.encode(initial)).set(UserDO::getEnabled, true)
                .set(UserDO::getMustChangePassword, true));
        // 改密、启用和版本递增在同一事务内完成，缓存仅在提交成功后清理。
        versions.bumpAuthVersion(admin.getId(), "force_password_change");
        invalidator.users(List.of(admin.getId()));
    }
}
