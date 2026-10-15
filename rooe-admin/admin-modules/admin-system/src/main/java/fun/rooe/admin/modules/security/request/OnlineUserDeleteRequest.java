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
package fun.rooe.admin.modules.security.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Set;

/**
 * 在线用户强退请求：包装待踢出的加密 token 集合。
 */
@Getter
@Setter
public class OnlineUserDeleteRequest implements Serializable {

    @NotEmpty(message = "会话标识不能为空")
    @Schema(description = "待强退的加密会话标识集合", requiredMode = Schema.RequiredMode.REQUIRED)
    private Set<@NotBlank(message = "会话标识不能为空") String> keys;
}
