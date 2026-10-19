package fun.rooe.admin.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import fun.rooe.admin.framework.security.AuthFailure;
import fun.rooe.admin.framework.security.AuthTelemetry;
import fun.rooe.admin.framework.security.config.AuthProperties;
import fun.rooe.admin.framework.security.model.LoginUser;
import fun.rooe.admin.framework.security.service.AuthIdentityService;
import fun.rooe.admin.framework.security.service.SessionService;
import fun.rooe.admin.framework.security.service.TokenService;
import fun.rooe.admin.infrastructure.config.properties.FileProperties;
import fun.rooe.admin.infrastructure.security.RedisAuthCache;
import fun.rooe.admin.infrastructure.utils.RedisUtils;
import fun.rooe.admin.model.UserDataScope;
import fun.rooe.admin.modules.security.domain.request.PasswordChangeRequest;
import fun.rooe.admin.modules.security.mapper.AuthMapper;
import fun.rooe.admin.modules.security.service.*;
import fun.rooe.admin.modules.security.service.impl.AuthServiceImpl;
import fun.rooe.admin.modules.system.converter.*;
import fun.rooe.admin.modules.system.domain.request.*;
import fun.rooe.admin.modules.system.service.*;
import fun.rooe.admin.modules.system.service.impl.MenuServiceImpl;
import fun.rooe.admin.modules.system.service.impl.RoleServiceImpl;
import fun.rooe.admin.modules.system.service.impl.UserCredentialServiceImpl;
import fun.rooe.admin.modules.system.service.impl.UserServiceImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Actual MyBatis, SQL row locks, Spring transactions and AFTER_COMMIT listeners on an isolated H2 database.
 */
@SpringJUnitConfig(AuthMutationIntegrationTest.Config.class)
class AuthMutationIntegrationTest {
    @Autowired
    DataSource dataSource;
    @Autowired
    PlatformTransactionManager transactionManager;
    @Autowired
    UserService users;
    @Autowired
    RoleService roles;
    @Autowired
    MenuService menus;
    @Autowired
    AuthService auth;
    @Autowired
    AuthMapper mapper;
    @Autowired
    AuthVersionService versions;
    @Autowired
    AuthCacheInvalidator invalidator;
    @Autowired
    RedisAuthCache cache;
    @Autowired
    AuthProperties properties;
    private JdbcTemplate sql;

    private static void actor(long id) {
        var principal = new LoginUser(id, "user" + id, "User", "sid", false, List.of());
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    private static IdReference ref(long id) {
        IdReference reference = new IdReference();
        reference.setId(id);
        return reference;
    }

    @BeforeEach
    void seed() {
        sql = new JdbcTemplate(dataSource);
        sql.execute("DROP ALL OBJECTS");
        sql.execute("CREATE TABLE sys_user (id BIGINT AUTO_INCREMENT PRIMARY KEY, dept_id BIGINT, username VARCHAR(180) UNIQUE, nick_name VARCHAR(255), gender VARCHAR(2), phone VARCHAR(255), email VARCHAR(180), avatar_name VARCHAR(255), avatar_path VARCHAR(255), password VARCHAR(255), is_admin BOOLEAN, enabled BOOLEAN, auth_version BIGINT DEFAULT 0 NOT NULL, must_change_password BOOLEAN DEFAULT FALSE, last_login_at TIMESTAMP, last_login_ip VARCHAR(64), create_by VARCHAR(255), update_by VARCHAR(255), pwd_reset_time TIMESTAMP, create_time TIMESTAMP, update_time TIMESTAMP)");
        sql.execute("CREATE TABLE sys_role (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255), role_code VARCHAR(64) UNIQUE, enabled BOOLEAN DEFAULT TRUE, level INT, data_scope VARCHAR(255), description VARCHAR(255), create_by VARCHAR(255), update_by VARCHAR(255), create_time TIMESTAMP, update_time TIMESTAMP)");
        sql.execute("CREATE TABLE sys_menu (id BIGINT AUTO_INCREMENT PRIMARY KEY, pid BIGINT, sub_count INT DEFAULT 0, type INT, title VARCHAR(255), name VARCHAR(255), component VARCHAR(255), menu_sort INT, icon VARCHAR(255), path VARCHAR(255), i_frame BOOLEAN, cache BOOLEAN, hidden BOOLEAN, enabled BOOLEAN DEFAULT TRUE, permission VARCHAR(255), create_by VARCHAR(255), update_by VARCHAR(255), create_time TIMESTAMP, update_time TIMESTAMP)");
        sql.execute("CREATE TABLE sys_user_role (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT, role_id BIGINT, UNIQUE(user_id, role_id))");
        sql.execute("CREATE TABLE sys_role_menu (id BIGINT AUTO_INCREMENT PRIMARY KEY, role_id BIGINT, menu_id BIGINT, UNIQUE(role_id, menu_id))");
        sql.execute("CREATE TABLE sys_user_job (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT, job_id BIGINT, UNIQUE(user_id, job_id))");
        sql.execute("CREATE TABLE sys_role_dept (id BIGINT AUTO_INCREMENT PRIMARY KEY, role_id BIGINT, dept_id BIGINT, UNIQUE(role_id, dept_id))");
        String hash = new BCryptPasswordEncoder().encode("OldPassword8!");
        for (int id = 1; id <= 3; id++) {
            sql.update("INSERT INTO sys_user(id, username, nick_name, dept_id, enabled, is_admin, password, auth_version, must_change_password) VALUES(?, ?, 'User', 10, TRUE, FALSE, ?, 0, FALSE)", id, "user" + id, hash);
        }
        sql.update("INSERT INTO sys_role(id, name, role_code, level, data_scope, enabled) VALUES(1,'Admin','SUPER_ADMIN',1,?,TRUE),(2,'Operator','USER',3,?,TRUE)",
                fun.rooe.admin.enums.DataScopeEnum.ALL.getValue(), fun.rooe.admin.enums.DataScopeEnum.ALL.getValue());
        sql.execute("INSERT INTO sys_user_role(user_id,role_id) VALUES(1,1),(2,2),(3,2)");
        sql.execute("INSERT INTO sys_menu(id, title, type, permission, enabled) VALUES(1,'List',2,'user:list',TRUE),(2,'Delete',2,'user:del',TRUE)");
        sql.execute("INSERT INTO sys_role_menu(role_id,menu_id) VALUES(2,1)");
        reset(cache);
        properties.setInitialPassword("InitialPassword8!");
        actor(1);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private long version(long id) {
        return sql.queryForObject("SELECT auth_version FROM sys_user WHERE id = ?", Long.class, id);
    }

    private UserUpdateRequest update(long id, boolean enabled, long role) {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setId(id);
        request.setUsername("user" + id);
        request.setNickName("User");
        request.setEnabled(enabled);
        request.setDept(ref(10));
        request.setRoles(Set.of(ref(role)));
        request.setJobs(Set.of());
        return request;
    }

    @Test
    void disableAndReenableIncrementOnlyOnceAndInvalidateAfterCommit() {
        users.update(update(2, false, 2));
        assertEquals(1, version(2));
        verify(cache).delete(argThat(keys -> keys.contains(RedisAuthCache.USER + 2)));
        users.update(update(2, true, 2));
        assertEquals(1, version(2));
    }

    @Test
    void passwordResetChangesHashForcesPasswordChangeAndBumpsVersionInTheSameTransaction() {
        var request = new UserResetPasswordRequest();
        request.setIds(Set.of(2L));
        users.resetPwd(request, "ResetPassword8!");
        assertEquals(1, version(2));
        var user = mapper.findUser(2);
        assertTrue(user.getMustChangePassword());
        assertTrue(new BCryptPasswordEncoder().matches("ResetPassword8!", user.getPassword()));
        verify(cache).delete(argThat(keys -> keys.contains(RedisAuthCache.USER + 2)));
    }

    @Test
    void changeOwnPasswordClearsFlagAndUses400ForIncorrectOldPassword() {
        actor(2);
        sql.update("UPDATE sys_user SET must_change_password=TRUE WHERE id=2");
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setOldPassword("wrong");
        request.setNewPassword("NewPassword8!");
        // 超过原改密尝试配额后仍校验旧密码，不产生 429，也不修改认证版本。
        for (int attempt = 0; attempt < 12; attempt++) {
            AuthFailure failure = assertThrows(AuthFailure.class, () -> auth.changePassword(request));
            assertEquals(400, failure.getStatus());
            assertEquals(fun.rooe.admin.error.AuthErrorCode.OLD_PASSWORD_INCORRECT, failure.getErrorCode());
            assertEquals(0, version(2));
        }
        request.setOldPassword("OldPassword8!");
        auth.changePassword(request);
        assertEquals(1, version(2));
        assertFalse(mapper.findUser(2).getMustChangePassword());
        verify(cache).delete(argThat(keys -> keys.contains(RedisAuthCache.USER + 2)));
    }

    @Test
    void deleteBumpsVersionBeforeDeletionAndBatchViolationRollsEverythingBack() {
        var request = new UserDeleteRequest();
        request.setIds(new LinkedHashSet<>(List.of(2L, 1L)));
        assertThrows(AuthFailure.class, () -> users.delete(request));
        assertEquals(0, version(2));
        verifyNoInteractions(cache);
        request.setIds(Set.of(2L));
        users.delete(request);
        assertNull(mapper.findUser(2));
        verify(cache).delete(argThat(keys -> keys.contains(RedisAuthCache.USER + 2)));
    }

    @Test
    void outerRollbackRestoresVersionBusinessStateAndSuppressesInvalidation() {
        var tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            users.update(update(2, false, 2));
            assertEquals(1, version(2));
            status.setRollbackOnly();
        });
        assertEquals(0, version(2));
        assertTrue(mapper.findUser(2).getEnabled());
        verifyNoInteractions(cache);
    }

    @Test
    void userRolesAndRolePermissionsInvalidateWithoutRevokingSessions() {
        users.update(update(2, true, 1));
        assertEquals(0, version(2));
        verify(cache).delete(argThat(keys -> keys.contains(RedisAuthCache.USER + 2)));
        reset(cache);
        RoleMenuUpdateRequest request = new RoleMenuUpdateRequest();
        request.setId(2L);
        request.setMenus(Set.of(ref(2)));
        roles.updateMenu(request);
        assertEquals(0, version(3));
        verify(cache).delete(argThat(keys -> keys.contains(RedisAuthCache.ROLE + 2)));
    }

    @Test
    void permissionEditAndRoleDeletionInvalidateRelatedCaches() {
        MenuUpdateRequest request = new MenuUpdateRequest();
        request.setId(1L);
        request.setTitle("List");
        request.setPermission("user:read");
        request.setEnabled(true);
        menus.update(request);
        verify(cache).delete(argThat(keys -> keys.contains(RedisAuthCache.ROLE + 2)));
        sql.execute("DELETE FROM sys_user_role WHERE role_id=2");
        reset(cache);
        RoleDeleteRequest deleted = new RoleDeleteRequest();
        deleted.setIds(Set.of(2L));
        roles.delete(deleted);
        verify(cache).delete(argThat(keys -> keys.contains(RedisAuthCache.ROLE + 2)));
    }

    @Test
    void noTransactionCannotPublishInvalidationOrBumpVersions() {
        assertThrows(IllegalStateException.class, () -> versions.bumpAuthVersion(2, "disabled"));
        assertThrows(IllegalStateException.class, () -> invalidator.users(List.of(2L)));
    }

    @Test
    void invalidationOutageRetriesThreeTimesAfterCommitWithoutRollingBackDatabase() {
        doThrow(new org.springframework.data.redis.RedisConnectionFailureException("offline")).when(cache).delete(anyCollection());
        users.update(update(2, false, 2));
        assertEquals(1, version(2));
        assertFalse(mapper.findUser(2).getEnabled());
        verify(cache, times(3)).delete(anyCollection());
    }

    @Test
    void nonSuperCannotModifyOthersProtectedAccountOrOwnPermissions() {
        actor(2);
        assertThrows(AuthFailure.class, () -> users.update(update(1, true, 1)));
        var request = new RoleMenuUpdateRequest();
        request.setId(2L);
        request.setMenus(Set.of(ref(2)));
        assertThrows(AuthFailure.class, () -> roles.updateMenu(request));
        var menu = new MenuUpdateRequest();
        menu.setId(1L);
        menu.setTitle("List");
        menu.setPermission("user:del");
        menu.setEnabled(true);
        assertThrows(AuthFailure.class, () -> menus.update(menu));
        assertEquals(0, version(2));
        verifyNoInteractions(cache);
    }

    @Test
    void nonAdminCannotGrantMorePermissionsOrModifyOwnRoleAndSuperRoleIsProtected() {
        actor(2);
        AuthFailure denied = assertThrows(AuthFailure.class, () -> users.update(update(3, true, 1)));
        assertEquals(403, denied.getStatus());
        assertThrows(AuthFailure.class, () -> users.update(update(2, true, 1)));
        actor(1);
        var deleted = new RoleDeleteRequest();
        deleted.setIds(Set.of(1L));
        assertThrows(AuthFailure.class, () -> roles.delete(deleted));
        var request = new RoleMenuUpdateRequest();
        request.setId(1L);
        request.setMenus(Set.of());
        assertThrows(AuthFailure.class, () -> roles.updateMenu(request));
    }

    @Test
    void existingUserAndRoleHttpEndpointsReturn403ForPrivilegeViolations() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                        new fun.rooe.admin.modules.system.controller.UserController(properties, users),
                        new fun.rooe.admin.modules.system.controller.RoleController(roles))
                .setControllerAdvice(new fun.rooe.admin.framework.advice.GlobalExceptionAdvice(mock(AuthTelemetry.class))).build();
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        sql.execute("INSERT INTO sys_role(id,name,role_code,level,data_scope,enabled) VALUES(3,'Privileged','OTHER',3,'all',TRUE)");
        sql.execute("INSERT INTO sys_role_menu(role_id,menu_id) VALUES(3,2)");
        actor(2);
        for (long target : List.of(2L, 3L, 1L)) {
            var request = update(target, true, target == 3 ? 3 : 1);
            request.setJobs(Set.of(ref(1)));
            request.setEmail("user" + target + "@example.com");
            request.setPhone("13800000000");
            var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/users")
                    .contentType("application/json").content(json.writeValueAsBytes(request))).andReturn().getResponse();
            assertEquals(403, response.getStatus());
            assertTrue(response.getContentAsString().contains("AUTH_"));
        }
        for (long role : List.of(1L, 2L, 3L)) {
            var request = new RoleMenuUpdateRequest();
            request.setId(role);
            request.setMenus(role == 3 ? Set.of() : Set.of(ref(2)));
            assertEquals(403, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/roles/menu")
                    .contentType("application/json").content(json.writeValueAsBytes(request))).andReturn().getResponse().getStatus());
        }
        actor(1);
        assertEquals(403, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/roles")
                .contentType("application/json").content("{\"ids\":[1]}")).andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/users")
                .contentType("application/json").content("{\"ids\":[1]}")).andReturn().getResponse().getStatus());
    }

    @Test
    void concurrentAdministratorsCannotDisableTheLastEnabledSuperAdministrator() throws Exception {
        sql.execute("UPDATE sys_user_role SET role_id=1 WHERE user_id=2");
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (long actorId : List.of(1L, 2L))
                futures.add(pool.submit(() -> {
                    actor(actorId);
                    try {
                        start.await();
                        users.update(update(actorId == 1 ? 2 : 1, false, 1));
                    } catch (AuthFailure expected) {
                        assertEquals(403, expected.getStatus());
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            start.countDown();
            for (var future : futures) future.get(15, TimeUnit.SECONDS);
            assertEquals(1, mapper.enabledSuperUsers("SUPER_ADMIN"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackages = {"fun.rooe.admin.modules.system.mapper", "fun.rooe.admin.modules.security.mapper"})
    @Import({UserServiceImpl.class, RoleServiceImpl.class, MenuServiceImpl.class, UserCredentialServiceImpl.class,
            AdminPrivilegeGuard.class, AuthVersionService.class, AuthCacheInvalidator.class, UserCacheManager.class, AuthServiceImpl.class})
    static class Config {
        @Bean
        DataSource dataSource() {
            JdbcDataSource source = new JdbcDataSource();
            source.setURL("jdbc:h2:mem:auth;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
            return source;
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(source);
            var config = new MybatisConfiguration();
            config.setMapUnderscoreToCamelCase(true);
            config.setCacheEnabled(false);
            factory.setConfiguration(config);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/system/*.xml"));
            return factory.getObject();
        }

        @Bean
        AuthProperties properties() {
            return new AuthProperties();
        }

        @Bean
        AuthTelemetry telemetry() {
            return mock(AuthTelemetry.class);
        }

        @Bean
        RedisAuthCache cache() {
            return mock(RedisAuthCache.class);
        }

        @Bean
        RedisUtils redis() {
            return mock(RedisUtils.class);
        }

        @Bean
        FileProperties files() {
            return new FileProperties();
        }

        @Bean
        DataService data() {
            DataService service = mock(DataService.class);
            when(service.getCurrentDataScope()).thenReturn(UserDataScope.all());
            return service;
        }

        @Bean
        DeptService departments() {
            return mock(DeptService.class);
        }

        @Bean
        PasswordEncoder passwords() {
            return new BCryptPasswordEncoder();
        }

        @Bean
        AuthenticationManager authenticationManager() {
            return mock(AuthenticationManager.class);
        }

        @Bean
        SessionService sessions() {
            return mock(SessionService.class);
        }

        @Bean
        AuthIdentityService identities() {
            return mock(AuthIdentityService.class);
        }

        @Bean
        TokenService tokens() {
            return new TokenService();
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        UserAssembler userAssembler() {
            return Mappers.getMapper(UserAssembler.class);
        }

        @Bean
        DeptAssembler deptAssembler() {
            return Mappers.getMapper(DeptAssembler.class);
        }

        @Bean
        RoleAssembler roleAssembler() {
            return Mappers.getMapper(RoleAssembler.class);
        }

        @Bean
        JobAssembler jobAssembler() {
            return Mappers.getMapper(JobAssembler.class);
        }

        @Bean
        MenuAssembler menuAssembler() {
            return Mappers.getMapper(MenuAssembler.class);
        }
    }
}
