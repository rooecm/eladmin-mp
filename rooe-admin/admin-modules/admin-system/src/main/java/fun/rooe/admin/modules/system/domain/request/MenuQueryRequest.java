package fun.rooe.admin.modules.system.domain.request;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Supported filters for menu queries.
 */
@Data
public class MenuQueryRequest {
    private String blurry;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private List<LocalDateTime> createTime;
    private Boolean pidIsNull;
    private Long pid;
}
