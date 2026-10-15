package fun.rooe.admin.modules.system.domain.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

/**
 * Request for resolving the ancestor chains of selected menus.
 */
@Data
public class MenuSuperiorRequest {
    @NotEmpty
    private List<@NotNull @Positive Long> ids;
    private boolean exclude = true;
}
