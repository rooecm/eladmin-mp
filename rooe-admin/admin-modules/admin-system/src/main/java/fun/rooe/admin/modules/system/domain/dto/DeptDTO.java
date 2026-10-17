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
package fun.rooe.admin.modules.system.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Department response object.
 *
 * @author Zheng Jie
 */
@Getter
@Setter
public class DeptDTO implements Serializable {

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "创建时间: yyyy-MM-dd HH:mm:ss")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新时间: yyyy-MM-dd HH:mm:ss")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "子部门")
    private List<DeptDTO> children;

    @Schema(description = "排序")
    private Integer deptSort;

    @Schema(description = "部门名称")
    private String name;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "上级部门")
    private Long pid;

    @Schema(description = "子节点数目")
    private Integer subCount;

    @Schema(description = "是否有子节点")
    public Boolean getHasChildren() {
        return subCount != null && subCount > 0;
    }

    @Schema(description = "是否为叶子")
    public Boolean getLeaf() {
        return subCount == null || subCount <= 0;
    }

    @Schema(description = "标签名称")
    public String getLabel() {
        return name;
    }
}
