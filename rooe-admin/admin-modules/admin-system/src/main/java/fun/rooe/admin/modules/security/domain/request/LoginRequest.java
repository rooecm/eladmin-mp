package fun.rooe.admin.modules.security.domain.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank
    @Size(max = 180)
    private String username;
    @NotBlank
    @Size(max = 72)
    @lombok.ToString.Exclude
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    @Size(max = 32)
    private String clientType;
}
