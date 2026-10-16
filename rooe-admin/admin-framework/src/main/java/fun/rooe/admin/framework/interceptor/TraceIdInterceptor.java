package fun.rooe.admin.framework.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

public class TraceIdInterceptor implements HandlerInterceptor {

    public static final String TRACE_ID_HEADER = "traceId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String traceId = MDC.get("traceId");
        if (StringUtils.hasText(traceId)) {
            response.setHeader(TRACE_ID_HEADER, traceId);
        }
        return true;
    }
}
