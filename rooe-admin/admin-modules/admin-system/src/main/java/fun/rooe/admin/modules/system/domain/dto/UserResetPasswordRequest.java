package fun.rooe.admin.modules.system.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.Set;

/**
 * Request payload for resetting selected users to the system default password.
 */
@Data
public class UserResetPasswordRequest {
    @NotEmpty
    private Set<@NotNull @Positive Long> ids;
}
