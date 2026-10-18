package fun.rooe.admin.modules.system.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Role response model with optional menu and department associations.
 */
@Data
public class RoleDTO {
    private Long id;
    private String name;
    private Integer level;
    private String dataScope;
    private String description;
    private Set<MenuDTO> menus;
    private Set<DeptDTO> depts;
    private String createBy;
    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
