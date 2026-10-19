package fun.rooe.admin.modules.security.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import fun.rooe.admin.framework.security.PermissionChecker;
import fun.rooe.admin.framework.security.model.LoginUser;
import fun.rooe.admin.framework.utils.SecurityUtils;
import fun.rooe.admin.modules.security.domain.dto.CurrentUser;
import fun.rooe.admin.modules.security.domain.dto.LoginResponse;
import fun.rooe.admin.modules.security.domain.request.LoginRequest;
import fun.rooe.admin.modules.security.domain.request.PasswordChangeRequest;
import fun.rooe.admin.modules.security.service.AuthService;
import fun.rooe.admin.modules.system.domain.entity.MenuDO;
import fun.rooe.admin.modules.system.mapper.MenuMapper;
import fun.rooe.admin.modules.system.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 认证 HTTP 入口，负责请求校验与响应约定，凭证及会话流程委托给业务服务。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;
    private final UserService users;
    private final MenuMapper menus;
    private final PermissionChecker permissions;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        // 仅使用容器解析的来源地址；部署时应配置可信代理，不能直接信任任意转发请求头。
        String ip = http.getRemoteAddr();
        var verified = auth.verifyCredentials(request);
        return auth.establishSession(verified, request.getClientType(), ip, http.getHeader("User-Agent"));
    }

    @GetMapping("/me")
    public CurrentUser me(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        var principal = (LoginUser) SecurityUtils.getCurrentUser();
        Set<String> effective = principal.isMustChangePassword() ? Set.of() : menus.selectList(
                        Wrappers.<MenuDO>lambdaQuery().eq(MenuDO::getEnabled, true).isNotNull(MenuDO::getPermission))
                .stream().map(MenuDO::getPermission).filter(p -> !p.isBlank() && permissions.has(p)).collect(Collectors.toSet());
        return new CurrentUser(users.getLoginData(principal.getUsername()), effective, principal.isMustChangePassword());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        auth.logout(SecurityUtils.getToken());
    }

    @PostMapping("/touch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void touch() {
        // 续期已在认证过滤器完成，此端点仅提供可由用户活动触发的轻量请求。
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(@Valid @RequestBody PasswordChangeRequest request) {
        auth.changePassword(request);
    }
}
