package fun.rooe.admin.modules.system.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.Set;

/**
 * Request payload for creating a user and assigning department, roles, and jobs.
 */
@Data
public class UserCreateRequest {
    @Valid
    @NotNull
    private IdReference dept;

    @Valid
    @NotEmpty
    private Set<IdReference> roles;

    @Valid
    @NotEmpty
    private Set<IdReference> jobs;

    @NotBlank
    @Size(max = 180)
    private String username;

    @NotBlank
    @Size(max = 255)
    private String nickName;

    @NotBlank
    @Email
    @Size(max = 180)
    private String email;

    @NotBlank
    @Size(max = 255)
    private String phone;

    @Size(max = 2)
    private String gender;

    @NotNull
    private Boolean enabled;
}
