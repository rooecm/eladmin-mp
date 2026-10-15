package fun.rooe.admin.modules.system.domain.request;

import lombok.Data;

import java.sql.Timestamp;
import java.util.List;

/**
 * Supported filters for menu queries.
 */
@Data
public class MenuQueryRequest {
    private String blurry;
    private List<Timestamp> createTime;
    private Boolean pidIsNull;
    private Long pid;
}
