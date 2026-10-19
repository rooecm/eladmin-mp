package fun.rooe.admin.framework.security.config;

import fun.rooe.admin.error.AuthErrorCode;
import fun.rooe.admin.error.CommonErrorCode;
import fun.rooe.admin.framework.security.AuthTelemetry;
import fun.rooe.admin.framework.security.SecurityErrorResponseWriter;
import fun.rooe.admin.framework.security.SecurityPaths;
import fun.rooe.admin.framework.security.TokenAuthenticationFilter;
import fun.rooe.admin.framework.security.service.AuthIdentityService;
import fun.rooe.admin.framework.security.service.SessionService;
import fun.rooe.admin.framework.security.service.TokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.time.Clock;

/**
 * 组装无 HTTP Session 的安全链，接口权限由方法级注解继续校验。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SpringSecurityConfig {
    @Bean
    public Clock authClock() {
        return Clock.systemUTC();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        // 用户不存在时对外统一为凭证错误，避免暴露账号是否存在。
        provider.setHideUserNotFoundExceptions(true);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, TokenService tokens, SessionService sessions,
                                           AuthIdentityService identities, AuthProperties properties, SecurityErrorResponseWriter errors,
                                           AuthTelemetry telemetry, CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http
                .cors(c -> c.configurationSource(corsConfigurationSource))
                // 认证凭证仅通过 Authorization 请求头传递，不使用 Cookie 会话认证。
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> errors.write(res, HttpStatus.UNAUTHORIZED, AuthErrorCode.SESSION_INVALID))
                        .accessDeniedHandler((req, res, ex) -> errors.write(res, HttpStatus.FORBIDDEN, CommonErrorCode.ACCESS_DENIED)))
                .authorizeHttpRequests(a -> a
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(SecurityPaths.PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                // 只挂入安全过滤链，避免同时注册为 Servlet Filter 导致重复执行。
                .addFilterBefore(new TokenAuthenticationFilter(tokens, sessions, identities, properties, errors, telemetry),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
