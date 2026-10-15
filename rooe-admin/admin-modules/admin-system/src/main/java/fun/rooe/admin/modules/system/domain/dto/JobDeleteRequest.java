package fun.rooe.admin.modules.system.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.Set;

/**
 * Request payload for deleting jobs in one transaction.
 */
@Data
public class JobDeleteRequest {
    @NotEmpty
    private Set<@NotNull @Positive Long> ids;
}
