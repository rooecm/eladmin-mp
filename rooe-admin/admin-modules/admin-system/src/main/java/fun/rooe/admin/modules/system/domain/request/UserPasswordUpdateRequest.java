package fun.rooe.admin.modules.system.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request payload containing encrypted old and new passwords.
 */
@Data
public class UserPasswordUpdateRequest {
    @NotBlank
    @Size(max = 72)
    private String oldPass;

    @NotBlank
    @Size(min = 8, max = 72)
    private String newPass;
}
