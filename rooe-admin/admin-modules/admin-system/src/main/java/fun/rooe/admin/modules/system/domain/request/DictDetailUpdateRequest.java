package fun.rooe.admin.modules.system.domain.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Request payload for updating an existing dictionary entry.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DictDetailUpdateRequest extends DictDetailCreateRequest {
    @NotNull
    @Positive
    private Long id;
}
