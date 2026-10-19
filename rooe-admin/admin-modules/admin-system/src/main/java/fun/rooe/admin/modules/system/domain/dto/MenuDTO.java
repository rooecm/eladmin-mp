package fun.rooe.admin.modules.system.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Menu response model, including derived tree-view properties.
 */
@Data
public class MenuDTO {
    private Long id;
    private Boolean enabled;
    private Long pid;
    private Integer subCount;
    private Integer type;
    private String title;
    private String componentName;
    private String component;
    private Integer menuSort;
    private String icon;
    private String path;

    @JsonProperty("iFrame")
    private Boolean iFrame;

    private Boolean cache;
    private Boolean hidden;
    private String permission;
    private String createBy;
    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    private List<MenuDTO> children;

    public Boolean getHasChildren() {
        return subCount != null && subCount > 0;
    }

    public Boolean getLeaf() {
        return subCount == null || subCount <= 0;
    }

    public String getLabel() {
        return title;
    }
}
