/*
 *  Copyright 2019-2025 Zheng Jie
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package fun.rooe.admin.modules.security.controller;

import com.wf.captcha.base.Captcha;
import fun.rooe.admin.error.AuthErrorCode;
import fun.rooe.admin.framework.annotation.Log;
import fun.rooe.admin.framework.annotation.rest.AnonymousDeleteMapping;
import fun.rooe.admin.framework.annotation.rest.AnonymousGetMapping;
import fun.rooe.admin.framework.annotation.rest.AnonymousPostMapping;
import fun.rooe.admin.framework.config.properties.RsaProperties;
import fun.rooe.admin.framework.config.security.config.CaptchaConfig;
import fun.rooe.admin.framework.config.security.config.LoginProperties;
import fun.rooe.admin.framework.config.security.config.SecurityProperties;
import fun.rooe.admin.framework.config.security.config.enums.LoginCodeEnum;
import fun.rooe.admin.framework.config.security.security.TokenProvider;
import fun.rooe.admin.framework.utils.SecurityUtils;
import fun.rooe.admin.infrastructure.utils.RedisUtils;
import fun.rooe.admin.modules.security.domain.dto.CaptchaResponse;
import fun.rooe.admin.modules.security.domain.dto.JwtUserDTO;
import fun.rooe.admin.modules.security.domain.dto.LoginResponse;
import fun.rooe.admin.modules.security.domain.request.LoginRequest;
import fun.rooe.admin.modules.security.service.OnlineUserService;
import fun.rooe.admin.modules.security.service.UserDetailsServiceImpl;
import fun.rooe.admin.modules.system.service.UserCredentialService;
import fun.rooe.admin.utils.BusinessAssert;
import fun.rooe.admin.utils.RsaUtils;
import fun.rooe.admin.utils.StringUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author Zheng Jie
 * @date 2018-11-23 授权、根据token获取用户详细信息
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "系统：系统授权接口")
public class AuthController {

    private final SecurityProperties properties;
    private final RedisUtils redisUtils;
    private final OnlineUserService onlineUserService;
    private final TokenProvider tokenProvider;
    private final CaptchaConfig captchaConfig;
    private final LoginProperties loginProperties;
    private final UserCredentialService userCredentialService;
    private final UserDetailsServiceImpl userDetailsService;

    @Log("用户登录")
    @Operation(summary = "登录授权")
    @AnonymousPostMapping(value = "/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest)
            throws Exception {
        // 密码解密
        String password =
                RsaUtils.decryptByPrivateKey(RsaProperties.privateKey, request.getPassword());
        // 查询验证码
        String code = redisUtils.get(request.getUuid(), String.class);
        // 清除验证码
        redisUtils.del(request.getUuid());
        BusinessAssert.isBlank(code, AuthErrorCode.CAPTCHA_INVALID, "验证码不存在或已过期");
        BusinessAssert.isTrue(!StringUtils.isBlank(request.getCode())
                && request.getCode().equalsIgnoreCase(code), AuthErrorCode.CAPTCHA_INVALID, "验证码错误");
        // 获取用户信息
        JwtUserDTO jwtUser = userDetailsService.loadUserByUsername(request.getUsername());
        // 验证用户密码
        if (!userCredentialService.matches(request.getUsername(), password)) {
            throw new BadCredentialsException(AuthErrorCode.BAD_CREDENTIALS.getMessage());
        }
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(jwtUser, null, jwtUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        // 生成令牌
        String token = tokenProvider.createToken(jwtUser.getUser().getId(), jwtUser.getUsername());
        String fullToken = properties.getTokenStartWith() + token;
        if (loginProperties.isSingleLogin()) {
            // 踢掉之前已经登录的token
            onlineUserService.kickOutForUsername(request.getUsername());
        }
        // 保存在线信息
        onlineUserService.save(jwtUser, token, httpRequest);
        // 返回登录信息
        return new LoginResponse(fullToken, jwtUser);
    }

    @Operation(summary = "获取用户信息")
    @GetMapping(value = "/info")
    public UserDetails getUserInfo() {
        return (JwtUserDTO) SecurityUtils.getCurrentUser();
    }

    @Operation(summary = "获取验证码")
    @AnonymousGetMapping(value = "/code")
    public CaptchaResponse getCode() {
        // 获取运算的结果
        Captcha captcha = captchaConfig.getCaptcha();
        String uuid = properties.getCodeKey() + UUID.randomUUID().toString().replace("-", "");
        // 当验证码类型为 arithmetic时且长度 >= 2 时，captcha.text()的结果有几率为浮点型
        String captchaValue = captcha.text();
        if (captcha.getCharType() - 1 == LoginCodeEnum.ARITHMETIC.ordinal()
                && captchaValue.contains(".")) {
            captchaValue = captchaValue.split("\\.")[0];
        }
        // 保存
        redisUtils.set(uuid, captchaValue, captchaConfig.getExpiration(), TimeUnit.MINUTES);
        // 验证码信息
        return new CaptchaResponse(captcha.toBase64(), uuid);
    }

    @Operation(summary = "退出登录")
    @AnonymousDeleteMapping(value = "/logout")
    public void logout(HttpServletRequest request) {
        String token = tokenProvider.getToken(request);
        onlineUserService.logout(token);
    }
}
