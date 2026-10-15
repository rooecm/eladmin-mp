package fun.rooe.admin.modules.system.domain.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.sql.Timestamp;
import java.util.List;

/**
 * Client-controlled filters and pagination parameters for user queries.
 */
@Data
public class UserQueryRequest {
    @Positive
    private Long id;
    private String blurry;
    private Boolean enabled;
    @Positive
    private Long deptId;
    private List<Timestamp> createTime;

    @Min(0)
    private Integer page = 1;

    @Min(1)
    @Max(10000)
    private Integer size = 10;
}
