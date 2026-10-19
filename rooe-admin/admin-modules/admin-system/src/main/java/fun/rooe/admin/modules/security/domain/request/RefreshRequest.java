package fun.rooe.admin.modules.security.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 凭证续期请求；Refresh 只在请求体传递，避免与 Authorization 头中的 Access 混淆。
 */
@Data
public class RefreshRequest {
    @NotBlank
    @Size(max = 128)
    @lombok.ToString.Exclude
    private String refreshToken;
}
