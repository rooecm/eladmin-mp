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
package fun.rooe.admin.framework.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.rooe.admin.enums.DataScopeEnum;
import fun.rooe.admin.model.UserDataScope;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * 获取当前登录的用户
 * @author Zheng Jie
 * @date 2019-01-17
 */
@Slf4j
@Component
public class SecurityUtils {

    public static String header;

    public static String tokenStartWith;

    @Value("${jwt.header}")
    public void setHeader(String header) {
        SecurityUtils.header = header;
    }

    @Value("${jwt.token-start-with}")
    public void setTokenStartWith(String tokenStartWith) {
        SecurityUtils.tokenStartWith = tokenStartWith;
    }

    /**
     * 获取当前登录的用户
     * @return UserDetails
     */
    public static UserDetails getCurrentUser() {
        UserDetailsService userDetailsService = SpringBeanHolder.getBean(UserDetailsService.class);
        return userDetailsService.loadUserByUsername(getCurrentUsername());
    }

    /**
     * 获取当前用户的数据权限
     * @return /
     */
    public static List<Long> getCurrentUserDataScope(){
        // 将 Java 对象转换为 JsonNode 对象
        JsonNode jsonNode = objectMapper().valueToTree(getCurrentUser());
        List<Long> dataScopes = new ArrayList<>();
        jsonNode.path("dataScopes").forEach(node -> dataScopes.add(node.asLong()));
        return dataScopes;
    }

    /**
     * 获取数据权限级别
     * @return 级别
     */
    public static String getDataScopeType() {
        return getCurrentUserDataScopeMode() == UserDataScope.Mode.ALL
                ? DataScopeEnum.ALL.getValue()
                : "";
    }

    public static UserDataScope.Mode getCurrentUserDataScopeMode() {
        JsonNode modeNode = objectMapper().valueToTree(getCurrentUser()).get("dataScopeMode");
        if (modeNode == null || modeNode.isNull()) {
            return getCurrentUserDataScope().isEmpty()
                    ? UserDataScope.Mode.NONE
                    : UserDataScope.Mode.LIMITED;
        }
        try {
            return UserDataScope.Mode.valueOf(modeNode.asText());
        } catch (IllegalArgumentException exception) {
            return UserDataScope.Mode.NONE;
        }
    }

    /**
     * 获取用户ID
     * @return 系统用户ID
     */
    public static Long getCurrentUserId() {
        return getCurrentUserId(getToken());
    }

    /**
     * 获取用户ID
     * @return 系统用户ID
     */
    public static Long getCurrentUserId(String token) {
        JsonNode userId = getClaims(token).get("userId");
        return userId == null || userId.isNull() ? null : userId.asLong();
    }

    /**
     * 获取系统用户名称
     *
     * @return 系统用户名称
     */
    public static String getCurrentUsername() {
        return getCurrentUsername(getToken());
    }

    /**
     * 获取系统用户名称
     *
     * @return 系统用户名称
     */
    public static String getCurrentUsername(String token) {
        JsonNode subject = getClaims(token).get("sub");
        return subject == null || subject.isNull() ? null : subject.asText();
    }

    /**
     * 获取Token
     * @return /
     */
    public static String getToken() {
        HttpServletRequest request = ((ServletRequestAttributes) Objects.requireNonNull(RequestContextHolder
                .getRequestAttributes())).getRequest();
        String bearerToken = request.getHeader(header);
        if (bearerToken != null && bearerToken.startsWith(tokenStartWith)) {
            // 去掉令牌前缀
            return bearerToken.replace(tokenStartWith, "");
        } else {
            log.debug("非法Token：{}", bearerToken);
        }
        return null;
    }

    private static JsonNode getClaims(String token) {
        String[] tokenParts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(tokenParts[1]), StandardCharsets.UTF_8);
        try {
            return objectMapper().readTree(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid token payload", exception);
        }
    }

    private static ObjectMapper objectMapper() {
        return SpringBeanHolder.getBean(ObjectMapper.class);
    }
}
