package fun.rooe.admin.framework.security;

import fun.rooe.admin.error.AuthErrorCode;
import lombok.Getter;

@Getter
public class AuthFailure extends RuntimeException {
    private final int status;
    private final AuthErrorCode errorCode;

    public AuthFailure(int status, AuthErrorCode code) {
        super(code.getMessage());
        this.status = status;
        this.errorCode = code;
    }
}
