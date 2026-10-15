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
package fun.rooe.admin.modules.security.dto;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import fun.rooe.admin.model.UserDataScope;
import fun.rooe.admin.modules.system.domain.dto.UserLoginDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Zheng Jie
 * @date 2018-11-23
 */
@Getter
@AllArgsConstructor
public class JwtUserDTO implements UserDetails {

    @Schema(description = "用户")
    private final UserLoginDTO user;

    @Schema(description = "数据权限")
    private final List<Long> dataScopes;

    @Schema(description = "Data scope mode")
    private final UserDataScope.Mode dataScopeMode;

    @Schema(description = "Whether the data scope also includes the current user")
    private final boolean dataScopeIncludesSelf;

    @Schema(description = "角色")
    private final List<AuthorityDTO> authorities;

    public JwtUserDTO(
            UserLoginDTO user, List<Long> dataScopes, List<AuthorityDTO> authorities) {
        this(
                user,
                dataScopes,
                dataScopes == null || dataScopes.isEmpty()
                        ? UserDataScope.Mode.NONE
                        : UserDataScope.Mode.LIMITED,
                false,
                authorities);
    }

    public Set<String> getRoles() {
        return authorities.stream().map(AuthorityDTO::getAuthority).collect(Collectors.toSet());
    }

    @Override
    @JsonIgnore
    @JSONField(serialize = false)
    public String getPassword() {
        // Passwords are verified through UserCredentialService and never enter the cached
        // principal.
        return null;
    }

    @Override
    @JSONField(serialize = false)
    public String getUsername() {
        return user.getUsername();
    }

    @JSONField(serialize = false)
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @JSONField(serialize = false)
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @JSONField(serialize = false)
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    @JSONField(serialize = false)
    public boolean isEnabled() {
        return user.getEnabled();
    }
}
