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
package me.zhengjie.modules.system.domain;

import com.alibaba.fastjson2.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import me.zhengjie.base.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
* @author Zheng Jie
* @date 2019-03-25
*/
@Getter
@Setter
@TableName("sys_dept")
public class Dept extends BaseEntity implements Serializable {

    @NotNull(groups = Update.class)
    @TableId(value="dept_id", type = IdType.AUTO)
    @Schema(description = "ID", hidden = true)
    private Long id;

    @TableField(exist = false)
    @JSONField(serialize = false)
    @Schema(description = "角色")
    private Set<Role> roles;

    @TableField(exist = false)
    @Schema(description = "子部门")
    private List<Dept> children;

    @Schema(description = "排序")
    private Integer deptSort;

    @NotBlank
    @Schema(description = "部门名称")
    private String name;

    @NotNull
    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "上级部门")
    private Long pid;

    @Schema(description = "子节点数目", hidden = true)
    private Integer subCount = 0;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Dept dept = (Dept) o;
        return Objects.equals(id, dept.id) &&
                Objects.equals(name, dept.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    @Schema(description = "是否有子节点")
    public Boolean getHasChildren() {
        return subCount > 0;
    }


    @Schema(description = "是否为叶子")
    public Boolean getLeaf() {
        return subCount <= 0;
    }

    @Schema(description = "标签名称")
    public String getLabel() {
        return name;
    }
}