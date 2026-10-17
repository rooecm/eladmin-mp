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
package fun.rooe.admin.modules.system.domain.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 部门查询请求。
 *
 * @author Zheng Jie
 * @date 2019-03-25
 */
@Data
public class DeptQueryRequest {

    @Schema(description = "部门名称")
    @Size(max = 255, message = "部门名称长度不能超过255个字符")
    private String name;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "上级部门")
    @Positive(message = "上级部门ID必须大于0")
    private Long pid;

    @Schema(description = "PID为空查询")
    private Boolean pidIsNull;

    @Schema(description = "创建时间")
    @Size(min = 2, max = 2, message = "创建时间必须包含开始时间和结束时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private List<LocalDateTime> createTime;
}
