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

import com.alibaba.fastjson2.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author Zheng Jie
 * @date 2018-12-17
 */
@Getter
@Setter
@TableName("sys_menu")
public class Menu implements Serializable {

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

    @NotNull(groups = {Update.class})
    @TableId(value="id", type = IdType.AUTO)
    @Schema(description = "ID", hidden = true)
    private Long id;

    @TableField(exist = false)
    @JSONField(serialize = false)
    @Schema(description = "菜单角色")
    private Set<Role> roles;

    @TableField(exist = false)
    private List<Menu> children;

    @Schema(description = "菜单标题")
    private String title;

    @TableField(value = "name")
    @Schema(description = "菜单组件名称")
    private String componentName;

    @Schema(description = "排序")
    private Integer menuSort = 999;

    @Schema(description = "组件路径")
    private String component;

    @Schema(description = "路由地址")
    private String path;

    @Schema(description = "菜单类型，目录、菜单、按钮")
    private Integer type;

    @Schema(description = "权限标识")
    private String permission;

    @Schema(description = "菜单图标")
    private String icon;

    @Schema(description = "缓存")
    private Boolean cache;

    @Schema(description = "是否隐藏")
    private Boolean hidden;

    @Schema(description = "上级菜单")
    private Long pid;

    @Schema(description = "子节点数目", hidden = true)
    private Integer subCount = 0;

    @Schema(description = "外链菜单")
    @JsonProperty("iFrame")
    private Boolean iFrame;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Menu menu = (Menu) o;
        return Objects.equals(id, menu.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
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
        return title;
    }
}
