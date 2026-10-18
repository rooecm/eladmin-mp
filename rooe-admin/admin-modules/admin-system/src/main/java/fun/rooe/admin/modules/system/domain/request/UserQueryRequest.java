package fun.rooe.admin.modules.system.domain.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
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
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private List<LocalDateTime> createTime;

    @Min(0)
    private Integer page = 1;

    @Min(1)
    @Max(10000)
    private Integer size = 10;
}
