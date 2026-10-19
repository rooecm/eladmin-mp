package fun.rooe.admin.framework.config.security.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.rooe.admin.error.ErrorCode;
import fun.rooe.admin.framework.security.AuthTelemetry;
import fun.rooe.admin.response.ApiResult;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class SecurityErrorResponseWriter {
    private final ObjectMapper objectMapper;
    private final AuthTelemetry telemetry;

    public void write(HttpServletResponse response, HttpStatus status, ErrorCode errorCode) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status.value());
        telemetry.count("auth_request_rejected_total", "status", Integer.toString(status.value()));
        if (status == HttpStatus.UNAUTHORIZED) {
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResult.failure(errorCode)));
    }
}
