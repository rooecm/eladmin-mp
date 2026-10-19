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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.rooe.admin.enums.DataScopeEnum;
import fun.rooe.admin.model.UserDataScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;

/**
 * 获取当前登录的用户
 * @author Zheng Jie
 * @date 2019-01-17
 */
@Slf4j
@Component
public class SecurityUtils {

    /**
     * Read only the principal established by the authentication filter.
     */
    public static UserDetails getCurrentUser() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails user)) {
            throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Authentication required");
        }
        return user;
    }

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
        return ((fun.rooe.admin.framework.security.model.LoginUser) getCurrentUser()).getId();
    }

    public static String getCurrentUsername() {
        return getCurrentUser().getUsername(); }
    public static String getToken() {
        var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        String header = attributes.getRequest().getHeader("Authorization");
        return header != null && header.regionMatches(true, 0, "Bearer ", 0, 7) ? header.substring(7) : null;
    }

    private static ObjectMapper objectMapper() {
        return SpringBeanHolder.getBean(ObjectMapper.class); }
}
