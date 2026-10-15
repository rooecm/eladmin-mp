package fun.rooe.admin.modules.system.domain.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Request payload for updating an existing role and its custom data scope.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RoleUpdateRequest extends RoleCreateRequest {
    @NotNull
    @Positive
    private Long id;
}
