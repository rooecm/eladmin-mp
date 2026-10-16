package fun.rooe.admin.framework.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.rooe.admin.exception.BadRequestException;
import fun.rooe.admin.framework.advice.ApiResultResponseAdvice;
import fun.rooe.admin.framework.advice.GlobalExceptionAdvice;
import fun.rooe.admin.framework.config.webConfig.ConfigurerAdapter;
import fun.rooe.admin.infrastructure.config.properties.FileProperties;
import fun.rooe.admin.response.ApiResult;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class TraceIdInterceptorTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .addInterceptors(new TraceIdInterceptor())
                .addFilters(new ConfigurerAdapter(new FileProperties()).corsFilter())
                .setControllerAdvice(new ApiResultResponseAdvice(), new GlobalExceptionAdvice())
                .build();
    }

    @Test
    void returnsTheLogTraceIdForSuccessEmptyAndWrappedResponses() throws Exception {
        String traceId = "0123456789abcdef0123456789abcdef";
        try (var ignored = MDC.putCloseable("traceId", traceId)) {
            for (String path : new String[]{"/trace/data", "/trace/empty", "/trace/result"}) {
                var response = mockMvc.perform(get(path).header("traceId", "client-value"))
                        .andReturn().getResponse();
                assertEquals(200, response.getStatus());
                assertEquals(traceId, response.getHeader("traceId"));
            }
        }
    }

    @Test
    void returnsTheTraceIdForErrorsAndCommittedDownloads() throws Exception {
        String traceId = "fedcba9876543210fedcba9876543210";
        try (var ignored = MDC.putCloseable("traceId", traceId)) {
            var error = mockMvc.perform(get("/trace/error")).andReturn().getResponse();
            assertEquals(200, error.getStatus());
            assertEquals("COMMON_1005", new ObjectMapper().readTree(error.getContentAsByteArray()).path("code").asText());
            assertEquals(traceId, error.getHeader("traceId"));

            var systemError = mockMvc.perform(get("/trace/system-error")).andReturn().getResponse();
            assertEquals(500, systemError.getStatus());
            assertEquals(traceId, systemError.getHeader("traceId"));

            var download = mockMvc.perform(get("/trace/download")).andReturn().getResponse();
            assertEquals("file", download.getContentAsString());
            assertEquals(traceId, download.getHeader("traceId"));
        }
    }

    @Test
    void exposesTheTraceIdToCrossOriginClients() throws Exception {
        try (var ignored = MDC.putCloseable("traceId", "0123456789abcdef0123456789abcdef")) {
            var response = mockMvc.perform(get("/trace/data").header(HttpHeaders.ORIGIN, "https://example.com"))
                    .andReturn().getResponse();
            assertEquals(200, response.getStatus());
            assertEquals("traceId", response.getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS));
            assertEquals(MDC.get("traceId"), response.getHeader("traceId"));
        }
    }

    @Test
    void doesNotReuseThePreviousRequestsTraceId() throws Exception {
        try (var ignored = MDC.putCloseable("traceId", "0123456789abcdef0123456789abcdef")) {
            mockMvc.perform(get("/trace/data"));
        }
        var response = mockMvc.perform(get("/trace/data").header("traceId", "client-value"))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertNull(response.getHeader("traceId"));
    }

    @RestController
    static class TestController {

        @GetMapping("/trace/data")
        Map<String, String> data() {
            return Map.of("name", "admin");
        }

        @GetMapping("/trace/empty")
        void empty() {
        }

        @GetMapping("/trace/result")
        ApiResult<Void> result() {
            return ApiResult.success();
        }

        @GetMapping("/trace/error")
        void error() {
            throw new BadRequestException("invalid request");
        }

        @GetMapping("/trace/download")
        void download(HttpServletResponse response) throws IOException {
            response.getWriter().write("file");
            response.flushBuffer();
        }

        @GetMapping("/trace/system-error")
        void systemError() {
            throw new IllegalStateException("unexpected error");
        }
    }
}
