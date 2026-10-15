package fun.rooe.admin.modules.system.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * Supported filters and pagination parameters for dictionary-entry queries.
 */
@Data
public class DictDetailQueryRequest {
    private String dictName;
    private String label;

    @Min(0)
    private Integer page = 1;

    @Min(1)
    @Max(10000)
    private Integer size = 10;
}
