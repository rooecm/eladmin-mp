package fun.rooe.admin.modules.system.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Request payload for updating an existing dictionary.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DictUpdateRequest extends DictCreateRequest {
    @NotNull
    @Positive
    private Long id;
}
