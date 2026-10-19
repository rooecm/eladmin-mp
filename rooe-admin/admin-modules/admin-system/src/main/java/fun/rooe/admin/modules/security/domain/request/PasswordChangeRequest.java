package fun.rooe.admin.modules.security.domain.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordChangeRequest {
    @NotBlank
    @Size(max = 72)
    @lombok.ToString.Exclude
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String oldPassword;
    @NotBlank
    @Size(min = 8, max = 72)
    @lombok.ToString.Exclude
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String newPassword;
}
