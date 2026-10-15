package fun.rooe.admin.modules.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request payload containing encrypted old and new passwords.
 */
@Data
public class UserPasswordUpdateRequest {
    @NotBlank
    private String oldPass;

    @NotBlank
    private String newPass;
}
