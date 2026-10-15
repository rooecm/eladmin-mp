package fun.rooe.admin.modules.system.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Frontend router response assembled from the current user's authorized menus.
 */
@Data
public class MenuRouterDto implements Serializable {

    @Schema(description = "路由名称")
    private String name;

    @Schema(description = "路由路径")
    private String path;

    @Schema(description = "是否隐藏")
    private Boolean hidden;

    @Schema(description = "重定向地址")
    private String redirect;

    @Schema(description = "前端组件")
    private String component;

    @Schema(description = "是否始终显示根菜单")
    private Boolean alwaysShow;

    @Schema(description = "路由元数据")
    private MenuRouterMetaDto meta;

    @Schema(description = "子路由")
    private List<MenuRouterDto> children;
}
