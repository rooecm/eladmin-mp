package fun.rooe.admin.modules.security;

import fun.rooe.admin.framework.security.AuthFailure;
import fun.rooe.admin.framework.security.AuthTelemetry;
import fun.rooe.admin.framework.security.config.SpringSecurityConfig;
import fun.rooe.admin.framework.security.service.AuthIdentityService;
import fun.rooe.admin.framework.security.service.SessionService;
import fun.rooe.admin.framework.security.service.TokenService;
import fun.rooe.admin.modules.security.domain.request.LoginRequest;
import fun.rooe.admin.modules.security.domain.request.RefreshRequest;
import fun.rooe.admin.modules.security.mapper.AuthMapper;
import fun.rooe.admin.modules.security.service.*;
import fun.rooe.admin.modules.security.service.impl.AuthServiceImpl;
import fun.rooe.admin.modules.system.domain.entity.UserDO;
import fun.rooe.admin.modules.system.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthLoginTest {
    private final AuthReadService reads = mock(AuthReadService.class);
    private final SessionService sessions = mock(SessionService.class);
    private final AuthIdentityService identities = mock(AuthIdentityService.class);
    private final AuthMapper mapper = mock(AuthMapper.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private AuthServiceImpl service;
    private UserDO user;

    @BeforeEach
    void setup() {
        var manager = new SpringSecurityConfig().authenticationManager(new LoginService(reads), encoder);
        service = new AuthServiceImpl(manager, new TokenService(), sessions, identities, mapper, mock(UserMapper.class),
                encoder, mock(AuthVersionService.class), mock(AuthCacheInvalidator.class), mock(AdminPrivilegeGuard.class),
                mock(AuthTelemetry.class), Clock.systemUTC());
        user = new UserDO();
        user.setId(2L);
        user.setUsername("alice");
        user.setEnabled(true);
        user.setPassword(encoder.encode("Password8!"));
        user.setAuthVersion(7L);
        user.setMustChangePassword(true);
        when(reads.login("alice")).thenReturn(user);
    }

    private LoginRequest request(String name, String password) {
        var request = new LoginRequest();
        request.setUsername(name);
        request.setPassword(password);
        return request;
    }

    @Test
    void nonexistentAndIncorrectPasswordsAreIndistinguishable() {
        assertThrows(BadCredentialsException.class, () -> service.verifyCredentials(request("missing", "WrongPass8!")));
        assertThrows(BadCredentialsException.class, () -> service.verifyCredentials(request("alice", "WrongPass8!")));
        verifyNoInteractions(sessions);
    }

    @Test
    void disabledStatusIsCheckedOnlyAfterCorrectPassword() {
        user.setEnabled(false);
        assertThrows(BadCredentialsException.class, () -> service.verifyCredentials(request("alice", "WrongPass8!")));
        assertThrows(DisabledException.class, () -> service.verifyCredentials(request("alice", "Password8!")));
        assertNull(user.getPassword());
        verifyNoInteractions(sessions);
    }

    @Test
    void singleCanonicalSnapshotSuppliesSessionVersionAndErasesHash() {
        var verified = service.verifyCredentials(request(" ALICE ", "Password8!"));
        assertNull(verified.getPassword());
        when(sessions.create(anyString(), anyString(), eq(2L), eq(7L), any(), eq("ip"), any()))
                .thenReturn(new fun.rooe.admin.framework.security.model.SessionInfo("sid", 2, 1, 1000, 7, false, 1800));
        var result = service.establishSession(verified, "WEB", "ip", "agent");
        assertEquals("SUCCESS", result.getStatus());
        assertTrue(result.isMustChangePassword());
        assertTrue(new TokenService().isValid(result.getAccessToken()));
        verify(reads, times(1)).login("alice");
    }

    @Test
    void repeatedIncorrectPasswordsDoNotBlockSubsequentLogin() {
        // 超过原账号尝试配额后，仍返回凭证错误，随后可用正确密码完成校验。
        for (int attempt = 0; attempt < 65; attempt++) {
            assertThrows(BadCredentialsException.class,
                    () -> service.verifyCredentials(request("alice", "WrongPass8!")));
        }
        assertNotNull(service.verifyCredentials(request("alice", "Password8!")));
        verifyNoInteractions(sessions);
    }

    @Test
    void repeatedSuccessfulLoginsContinueCreatingSessions() {
        String hash = user.getPassword();
        when(sessions.create(anyString(), anyString(), eq(2L), eq(7L), any(), eq("ip"), any()))
                .thenReturn(new fun.rooe.admin.framework.security.model.SessionInfo("sid", 2, 1, 1000, 7, false, 1800));
        // 模拟每次数据库查询取得完整密码快照，超过原成功次数配额后仍可创建会话。
        for (int attempt = 0; attempt < 35; attempt++) {
            user.setPassword(hash);
            var verified = service.verifyCredentials(request("alice", "Password8!"));
            assertEquals("SUCCESS", service.establishSession(verified, "WEB", "ip", "agent").getStatus());
            assertNull(verified.getPassword());
        }
        verify(sessions, times(35)).create(anyString(), anyString(), eq(2L), eq(7L), any(), eq("ip"), any());
    }

    @Test
    void refreshRotatesCredentialsAndRejectsUnknownTokens() {
        var request = new RefreshRequest();
        request.setRefreshToken("old-refresh");
        var replayed = new TokenService().digest("old-refresh");
        when(sessions.refresh(eq(replayed), anyString(), anyString())).thenReturn(null);
        assertEquals(401, assertThrows(AuthFailure.class, () -> service.refresh(request)).getStatus());

        when(sessions.refresh(eq(replayed), anyString(), anyString()))
                .thenReturn(new fun.rooe.admin.framework.security.model.SessionTokens("sid", 2, 1, 1000, 7, 900));
        when(identities.getUserForSession(2, 7))
                .thenReturn(new fun.rooe.admin.framework.security.model.UserSnapshot(2, "alice", "Alice", true, false, 7, List.of()));
        var result = service.refresh(request);
        assertTrue(new TokenService().isValid(result.getAccessToken()));
        assertTrue(new TokenService().isValid(result.getRefreshToken()));
        assertEquals(900, result.getExpiresIn());
    }

    @Test
    void refreshRevokesTheRotatedSessionWhenTheUserIsNoLongerValid() {
        var request = new RefreshRequest();
        request.setRefreshToken("old-refresh");
        when(sessions.refresh(anyString(), anyString(), anyString()))
                .thenReturn(new fun.rooe.admin.framework.security.model.SessionTokens("sid", 2, 1, 1000, 7, 900));
        when(identities.getUserForSession(2, 7)).thenReturn(null);

        assertEquals(401, assertThrows(AuthFailure.class, () -> service.refresh(request)).getStatus());
        verify(sessions).revokeBySessionId("sid");
    }

    @Test
    void databaseOutageFailsClosedWith503() {
        when(reads.login("alice")).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("secret"));
        assertEquals(503, assertThrows(AuthFailure.class, () -> service.verifyCredentials(request("alice", "Password8!"))).getStatus());
        verifyNoInteractions(sessions);
    }

    @Test
    void sessionOutageFailsClosedWith503AndErasesCredentials() {
        var verified = service.verifyCredentials(request("alice", "Password8!"));
        when(sessions.create(anyString(), anyString(), eq(2L), eq(7L), any(), eq("ip"), any()))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("secret"));
        assertEquals(503, assertThrows(AuthFailure.class,
                () -> service.establishSession(verified, "WEB", "ip", "agent")).getStatus());
        assertNull(verified.getPassword());
    }
}


