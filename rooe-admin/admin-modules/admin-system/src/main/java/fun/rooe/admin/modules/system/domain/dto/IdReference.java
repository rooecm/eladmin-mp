package fun.rooe.admin.modules.system.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * Nested reference used by association requests.
 */
@Data
public class IdReference {
    @NotNull
    @Positive
    private Long id;
}
