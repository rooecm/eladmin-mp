package fun.rooe.admin.modules.system.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.sql.Timestamp;
import java.util.List;

/**
 * Supported filters and pagination parameters for job queries.
 */
@Data
public class JobQueryRequest {
    private String name;
    private Boolean enabled;
    private List<Timestamp> createTime;

    @Min(0)
    private Integer page = 1;

    @Min(1)
    @Max(10000)
    private Integer size = 10;
}
