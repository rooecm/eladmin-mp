package fun.rooe.admin.modules.system.domain.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request payload for creating an entry under a dictionary.
 */
@Data
public class DictDetailCreateRequest {
    @Valid
    @NotNull
    private IdReference dict;

    @NotBlank
    @Size(max = 255)
    private String label;

    @NotBlank
    @Size(max = 255)
    private String value;

    @NotNull
    @PositiveOrZero
    private Integer dictSort;
}
