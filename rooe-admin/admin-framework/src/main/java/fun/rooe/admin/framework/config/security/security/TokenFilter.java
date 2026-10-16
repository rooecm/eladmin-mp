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
package fun.rooe.admin.framework.config.security.security;

import fun.rooe.admin.error.CommonErrorCode;
import fun.rooe.admin.framework.config.security.config.SecurityProperties;
import fun.rooe.admin.framework.config.security.service.OnlineSessionService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;

/**
 * @author /
 */
@Slf4j
public class TokenFilter extends GenericFilterBean {

    private final TokenProvider tokenProvider;
    private final SecurityProperties properties;
    private final OnlineSessionService onlineSessionService;
    private final SecurityErrorResponseWriter errorWriter;

    /**
     * @param tokenProvider     Token
     * @param properties        JWT
     * @param onlineSessionService 在线会话查询
     */
    public TokenFilter(TokenProvider tokenProvider, SecurityProperties properties, OnlineSessionService onlineSessionService,
                       SecurityErrorResponseWriter errorWriter) {
        this.properties = properties;
        this.onlineSessionService = onlineSessionService;
        this.tokenProvider = tokenProvider;
        this.errorWriter = errorWriter;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws ServletException, IOException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        String token = resolveToken(httpServletRequest);
        // 对于 Token 为空的不需要去查 Redis
        if (StringUtils.hasText(token)) {
            try {
                String loginKey = tokenProvider.loginKey(token);
                if (onlineSessionService.isOnline(loginKey)) {
                    tokenProvider.checkRenewal(token);
                    Authentication authentication = tokenProvider.getAuthentication(token);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    SecurityContextHolder.clearContext();
                }
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
            } catch (RuntimeException e) {
                SecurityContextHolder.clearContext();
                log.error("Failed to resolve authentication session", e);
                errorWriter.write((HttpServletResponse) servletResponse, HttpStatus.INTERNAL_SERVER_ERROR,
                        CommonErrorCode.INTERNAL_ERROR);
                return;
            }
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }

    /**
     * 初步检测Token
     *
     * @param request /
     * @return /
     */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(properties.getHeader());
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(properties.getTokenStartWith())) {
            // 去掉令牌前缀
            return bearerToken.substring(properties.getTokenStartWith().length()).trim();
        } else {
            log.debug("Authorization header does not contain a supported token");
        }
        return null;
    }
}
