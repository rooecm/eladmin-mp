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
package fun.rooe.admin.modules.security.domain.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 在线用户查询请求：只包含客户端可用的筛选与分页条件。
 */
@Data
public class OnlineUserQueryRequest implements Serializable {

    @Schema(description = "用户名")
    @Size(max = 255, message = "用户名长度不能超过255个字符")
    private String username;

    @Schema(description = "页码，从1开始")
    @Min(value = 1, message = "页码必须大于0")
    private Integer page = 1;

    @Schema(description = "每页条数")
    @Min(value = 1, message = "每页条数必须大于0")
    @Max(value = 10000, message = "每页条数不能超过10000")
    private Integer size = 10;
}
