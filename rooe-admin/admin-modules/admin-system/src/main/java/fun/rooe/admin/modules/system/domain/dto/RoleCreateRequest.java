package fun.rooe.admin.modules.system.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

/**
 * Request payload for creating a role and its custom data scope.
 */
@Data
public class RoleCreateRequest {
    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    @Positive
    private Integer level;

    @NotBlank
    @Size(max = 255)
    private String dataScope;

    @Size(max = 255)
    private String description;

    @Valid
    private Set<IdReference> depts;
}
