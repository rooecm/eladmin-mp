package fun.rooe.admin.modules.system.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Request payload for updating a user and replacing role and job associations.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserUpdateRequest extends UserCreateRequest {
    @NotNull
    @Positive
    private Long id;
}
