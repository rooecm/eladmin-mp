package fun.rooe.admin.modules.system.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * User response model with resolved department, role, and job associations.
 */
@Data
public class UserDTO {
    private Long id;
    private Long deptId;
    private DeptDTO dept;
    private Set<RoleDTO> roles;
    private Set<JobDTO> jobs;
    private String username;
    private String nickName;
    private String email;
    private String phone;
    private String gender;
    private String avatarName;
    private Boolean enabled;
    private Boolean isAdmin;
    private String createBy;
    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime pwdResetTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
