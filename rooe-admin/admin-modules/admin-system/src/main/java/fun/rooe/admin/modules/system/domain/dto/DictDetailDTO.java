package fun.rooe.admin.modules.system.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Dictionary-entry response model.
 */
@Data
public class DictDetailDTO {
    private Long id;
    private Long dictId;
    private DictDTO dict;
    private String label;
    private String value;
    private Integer dictSort;
    private String createBy;
    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
