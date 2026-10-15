package fun.rooe.admin.modules.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request payload for creating a job.
 */
@Data
public class JobCreateRequest {
    @NotBlank
    @Size(max = 255)
    private String name;

    @NotNull
    @PositiveOrZero
    private Long jobSort;

    @NotNull
    private Boolean enabled;
}
