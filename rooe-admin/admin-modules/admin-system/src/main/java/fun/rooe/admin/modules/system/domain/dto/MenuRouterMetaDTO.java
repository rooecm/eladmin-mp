package fun.rooe.admin.modules.system.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * Metadata consumed by the frontend router and sidebar.
 */
@Data
@AllArgsConstructor
public class MenuRouterMetaDTO implements Serializable {

    @Schema(description = "菜单标题")
    private String title;

    @Schema(description = "菜单图标")
    private String icon;

    @Schema(description = "是否禁用页面缓存")
    private Boolean noCache;
}
