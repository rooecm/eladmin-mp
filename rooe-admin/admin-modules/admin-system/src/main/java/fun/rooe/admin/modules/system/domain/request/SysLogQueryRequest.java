package fun.rooe.admin.modules.system.domain.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Supported filters and pagination parameters for log queries.
 */
@Data
public class SysLogQueryRequest {
    @Schema(description = "用户名、描述、地址、IP、方法或参数的模糊搜索")
    private String blurry;

    @Schema(description = "用户名模糊搜索，仅用于管理端查询")
    private String username;

    @Schema(description = "创建时间范围，包含开始和结束时间")
    @Size(min = 2, max = 2)
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private List<@NotNull LocalDateTime> createTime;

    @Schema(description = "页码，兼容前端从 0 开始的请求")
    @NotNull
    @Min(0)
    private Integer page = 1;

    @Schema(description = "每页条数")
    @NotNull
    @Min(1)
    @Max(10000)
    private Integer size = 10;
}
