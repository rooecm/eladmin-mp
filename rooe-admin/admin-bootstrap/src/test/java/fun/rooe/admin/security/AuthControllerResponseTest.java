package fun.rooe.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.rooe.admin.framework.advice.ApiResultResponseAdvice;
import fun.rooe.admin.framework.security.PermissionChecker;
import fun.rooe.admin.framework.security.model.LoginUser;
import fun.rooe.admin.modules.security.controller.AuthController;
import fun.rooe.admin.modules.security.domain.dto.LoginResponse;
import fun.rooe.admin.modules.security.domain.dto.LoginUserDetails;
import fun.rooe.admin.modules.security.domain.request.LoginRequest;
import fun.rooe.admin.modules.security.service.AuthService;
import fun.rooe.admin.modules.system.domain.dto.UserDTO;
import fun.rooe.admin.modules.system.domain.entity.MenuDO;
import fun.rooe.admin.modules.system.mapper.MenuMapper;
import fun.rooe.admin.modules.system.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 验证实际认证入口与统一响应 Advice 的协作，不启动数据库及 Redis。
 */
class AuthControllerResponseTest {
    private final AuthService auth = mock(AuthService.class);
    private final UserService users = mock(UserService.class);
    private final MenuMapper menus = mock(MenuMapper.class);
    private final PermissionChecker permissions = mock(PermissionChecker.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(auth, users, menus, permissions))
                .setControllerAdvice(new ApiResultResponseAdvice()).build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginReturnsOneEnvelopeAndKeepsCacheHeaders() throws Exception {
        var verified = mock(LoginUserDetails.class);
        when(auth.verifyCredentials(any(LoginRequest.class))).thenReturn(verified);
        when(auth.establishSession(verified, "WEB", "127.0.0.1", "test-agent"))
                .thenReturn(new LoginResponse("SUCCESS", "opaque-token", "Bearer", 1800, 43200, false, null, null));

        var response = mvc.perform(post("/api/auth/login").header("User-Agent", "test-agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"Password8!\",\"clientType\":\"WEB\"}"))
                .andReturn().getResponse();
        var body = mapper.readTree(response.getContentAsByteArray());

        assertEquals(200, response.getStatus());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals("no-cache", response.getHeader("Pragma"));
        assertEquals("0", body.path("code").asText());
        assertEquals("opaque-token", body.path("data").path("accessToken").asText());
        assertFalse(body.path("data").has("code"));
        verify(auth).establishSession(verified, "WEB", "127.0.0.1", "test-agent");
    }

    @Test
    void currentUserReturnsOneEnvelopeAndOnlyEffectivePermissions() throws Exception {
        authenticate(false);
        var allowed = new MenuDO();
        allowed.setPermission("user:list");
        var denied = new MenuDO();
        denied.setPermission("user:delete");
        when(menus.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(allowed, denied));
        when(permissions.has("user:list")).thenReturn(true);

        var response = mvc.perform(get("/api/auth/me")).andReturn().getResponse();
        var body = mapper.readTree(response.getContentAsByteArray());

        assertEquals(200, response.getStatus());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals("0", body.path("code").asText());
        assertEquals("alice", body.path("data").path("user").path("username").asText());
        assertEquals(mapper.readTree("[\"user:list\"]"), body.path("data").path("roles"));
        assertFalse(body.path("data").path("mustChangePassword").asBoolean());
        assertFalse(body.path("data").has("code"));
    }

    @Test
    void forcedPasswordChangeKeepsPermissionsEmpty() throws Exception {
        authenticate(true);

        var response = mvc.perform(get("/api/auth/me")).andReturn().getResponse();
        var body = mapper.readTree(response.getContentAsByteArray());

        assertEquals(200, response.getStatus());
        assertEquals("0", body.path("code").asText());
        assertTrue(body.path("data").path("mustChangePassword").asBoolean());
        assertEquals(mapper.readTree("[]"), body.path("data").path("roles"));
        verifyNoInteractions(menus, permissions);
    }

    @Test
    void logoutTouchAndPasswordKeepNoContentResponses() throws Exception {
        for (String path : List.of("logout", "touch", "password")) {
            var response = mvc.perform(post("/api/auth/" + path)
                            .header("Authorization", "Bearer opaque-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"oldPassword\":\"OldPass8!\",\"newPassword\":\"NewPass8!\"}"))
                    .andReturn().getResponse();
            assertEquals(204, response.getStatus(), path);
            assertEquals(0, response.getContentAsByteArray().length, path);
        }
        verify(auth).logout("opaque-token");
        verify(auth).changePassword(argThat(request -> "NewPass8!".equals(request.getNewPassword())));
    }

    private void authenticate(boolean mustChangePassword) {
        var principal = new LoginUser(2, "alice", "Alice", "session", mustChangePassword, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        var user = new UserDTO();
        user.setUsername("alice");
        when(users.getLoginData("alice")).thenReturn(user);
    }
}
