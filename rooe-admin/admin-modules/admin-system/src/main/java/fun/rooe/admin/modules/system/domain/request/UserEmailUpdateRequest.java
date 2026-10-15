package fun.rooe.admin.modules.system.domain.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request payload for changing the current user's email after verification.
 */
@Data
public class UserEmailUpdateRequest {
    @NotBlank
    private String password;

    @NotBlank
    @Email
    @Size(max = 180)
    private String email;
}
