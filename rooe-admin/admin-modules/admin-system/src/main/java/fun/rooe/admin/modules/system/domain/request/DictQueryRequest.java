package fun.rooe.admin.modules.system.domain.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Supported filters and pagination parameters for dictionary queries.
 */
@Data
public class DictQueryRequest {
    private String blurry;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private List<LocalDateTime> createTime;

    @Min(0)
    private Integer page = 1;

    @Min(1)
    @Max(10000)
    private Integer size = 10;
}
