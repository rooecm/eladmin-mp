package fun.rooe.admin.modules.system.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Request payload for creating a menu, route, or permission entry.
 */
@Data
public class MenuCreateRequest {
    private Long pid;

    @NotNull
    @Min(0)
    @Max(2)
    private Integer type;

    @NotBlank
    @Size(max = 255)
    private String title;

    @Size(max = 255)
    private String componentName;

    @Size(max = 255)
    private String component;

    @NotNull
    @PositiveOrZero
    private Integer menuSort;

    @Size(max = 255)
    private String icon;

    @Size(max = 255)
    private String path;

    @NotNull
    @JsonProperty("iFrame")
    private Boolean iFrame;

    @NotNull
    private Boolean cache;

    @NotNull
    private Boolean hidden;

    @Size(max = 255)
    private String permission;
}
