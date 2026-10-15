package fun.rooe.admin.modules.system.domain.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.Set;

/**
 * Request payload for deleting dictionaries in one transaction.
 */
@Data
public class DictDeleteRequest {
    @NotEmpty
    private Set<@NotNull @Positive Long> ids;
}
