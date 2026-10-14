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
package fun.rooe.admin.modules.system.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.sql.Timestamp;

/**
* @author Zheng Jie
* @date 2019-04-10
*/
@Getter
@Setter
@TableName("sys_dict")
public class Dict implements Serializable {

    /* 分组校验 */
    public @interface Create {}
    /* 分组校验 */
    public @interface Update {}

    @CreatedBy
    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建人", hidden = true)
    private String createBy;

    @LastModifiedBy
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新人", hidden = true)
    private String updateBy;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间: yyyy-MM-dd HH:mm:ss", hidden = true)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Timestamp createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间: yyyy-MM-dd HH:mm:ss", hidden = true)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Timestamp updateTime;

    @NotNull(groups = Update.class)
    @Schema(description = "ID", hidden = true)
    @TableId(value = "dict_id", type = IdType.AUTO)
    private Long id;

    @NotBlank
    @Schema(description = "名称")
    private String name;

    @Schema(description = "描述")
    private String description;
}