package fun.rooe.admin.modules.system.domain.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.Set;

/**
 * Request payload for replacing all menu associations of a role.
 */
@Data
public class RoleMenuUpdateRequest {
    @NotNull
    @Positive
    private Long id;

    @Valid
    @NotNull
    private Set<IdReference> menus;
}
