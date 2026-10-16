package fun.rooe.admin.framework.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.rooe.admin.error.AuthErrorCode;
import fun.rooe.admin.error.CommonErrorCode;
import fun.rooe.admin.exception.BadRequestException;
import fun.rooe.admin.exception.BusinessException;
import fun.rooe.admin.framework.advice.ApiResultResponseAdvice;
import fun.rooe.admin.framework.advice.GlobalExceptionAdvice;
import fun.rooe.admin.utils.BusinessAssert;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class ApiResultResponseAdviceTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .defaultResponseCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8)
                .setControllerAdvice(new ApiResultResponseAdvice(), new GlobalExceptionAdvice())
                .build();
    }

    @Test
    void wrapsControllerData() throws Exception {
        var response = mockMvc.perform(get("/test/data")).andReturn().getResponse();
        JsonNode body = objectMapper.readTree(response.getContentAsString());

        assertEquals(200, response.getStatus());
        assertEquals(false, body.has("status"));
        assertEquals("0", body.get("code").asText());
        assertEquals("admin", body.get("data").get("name").asText());
    }

    @Test
    void wrapsVoidAsSuccess() throws Exception {
        var response = mockMvc.perform(get("/test/void")).andReturn().getResponse();
        JsonNode body = objectMapper.readTree(response.getContentAsString());

        assertEquals(200, response.getStatus());
        assertEquals(false, body.has("status"));
        assertEquals("0", body.get("code").asText());
        assertEquals(true, body.get("data").isNull());
    }

    @Test
    void doesNotWrapDirectServletResponse() throws Exception {
        var response = mockMvc.perform(get("/test/download")).andReturn().getResponse();

        assertEquals(200, response.getStatus());
        assertEquals(MediaType.TEXT_PLAIN_VALUE, response.getContentType());
        assertEquals("file", response.getContentAsString());
    }

    @Test
    void doesNotWrapExceptionResultAgain() throws Exception {
        var response = mockMvc.perform(get("/test/error")).andReturn().getResponse();
        JsonNode body = objectMapper.readTree(response.getContentAsString());

        assertEquals(200, response.getStatus());
        assertEquals(false, body.has("status"));
        assertEquals("COMMON_1005", body.get("code").asText());
        assertEquals("invalid request", body.get("message").asText());
        assertEquals(true, body.get("data").isNull());
    }

    @Test
    void mapsUnexpectedExceptionToInternalServerError() throws Exception {
        var response = mockMvc.perform(get("/test/unexpected-error")).andReturn().getResponse();
        JsonNode body = objectMapper.readTree(response.getContentAsString());

        assertEquals(500, response.getStatus());
        assertEquals(false, body.has("status"));
        assertEquals("SYS_1001", body.get("code").asText());
        assertEquals("系统内部异常，请稍后重试", body.get("message").asText());
    }

    @Test
    void mapsKnownBusinessFailuresToDistinctCodes() throws Exception {
        assertError(get("/test/business/captcha"), 200, "AUTH_1007");
        assertError(get("/test/business/password"), 401, "AUTH_1001");
        assertError(get("/test/business/limit"), 200, "COMMON_1004");
        assertError(get("/test/business/duplicate"), 409, "COMMON_1003");
        assertError(get("/test/business/large"), 400, "COMMON_1008");
        assertError(get("/test/business/token"), 401, "AUTH_1005");
        assertError(get("/test/business/disabled"), 403, "AUTH_1002");
        assertError(get("/test/business/missing"), 200, "COMMON_1002");
        assertError(get("/test/business/exists"), 200, "COMMON_1003");
        assertError(get("/test/business/unregistered"), 200, "CUSTOM_1001");
    }

    @Test
    void internalFailuresAreNotReportedAsBadCredentialsOrDataConflicts() throws Exception {
        assertError(get("/test/business/auth-service"), 500, "SYS_1001");
        assertError(get("/test/business/integrity"), 500, "SYS_1001");
        assertError(get("/test/business/unauthenticated"), 401, "AUTH_1005");
        assertError(get("/test/business/unknown-user"), 401, "AUTH_1001");
    }

    @Test
    void authenticationProviderWrappersPreserveKnownRejections() throws Exception {
        assertError(get("/test/provider/disabled"), 403, "AUTH_1002");
        assertError(get("/test/provider/credentials"), 401, "AUTH_1001");
        assertError(get("/test/provider/backend"), 500, "SYS_1001");
    }

    @Test
    void unhandledFrameworkErrorsReturnInternalServerError() throws Exception {
        assertError(get("/test/framework/404"), 500, "SYS_1001");
        assertError(get("/test/framework/429"), 500, "SYS_1001");
        assertError(get("/test/framework/503"), 500, "SYS_1001");
        assertError(get("/test/no-such-route"), 500, "SYS_1001");
        assertError(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/test/upload"),
                400, "COMMON_1001");
    }

    @Test
    void distinguishesHandledRequestErrorsFromUnhandledProtocolErrors() throws Exception {
        assertError(get("/test/parameter"), 400, "COMMON_1001");
        assertError(get("/test/parameter").param("number", "abc"), 400, "COMMON_1001");
        assertError(post("/test/json").contentType(MediaType.APPLICATION_JSON).content("{"), 400, "COMMON_1001");
        var mediaError = assertError(post("/test/json").contentType(MediaType.TEXT_PLAIN).content("body"),
                500, "SYS_1001");
        assertEquals("系统内部异常，请稍后重试", mediaError.path("message").asText());
        var response = mockMvc.perform(post("/test/data")).andReturn().getResponse();
        assertEquals(500, response.getStatus());
        assertEquals("SYS_1001", objectMapper.readTree(response.getContentAsByteArray()).path("code").asText());
        assertEquals("系统内部异常，请稍后重试", objectMapper.readTree(response.getContentAsByteArray()).path("message").asText());
    }

    @Test
    void productionFastJsonConverterKeepsDataNullOnSuccessAndFailure() throws Exception {
        var converters = new java.util.ArrayList<org.springframework.http.converter.HttpMessageConverter<?>>();
        new fun.rooe.admin.framework.config.webConfig.ConfigurerAdapter(
                new fun.rooe.admin.infrastructure.config.properties.FileProperties()).configureMessageConverters(converters);
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setMessageConverters(converters.toArray(org.springframework.http.converter.HttpMessageConverter[]::new))
                .setControllerAdvice(new ApiResultResponseAdvice(), new GlobalExceptionAdvice()).build();
        var response = mockMvc.perform(get("/test/void")).andReturn().getResponse();
        var body = objectMapper.readTree(response.getContentAsByteArray());
        assertEquals("0", body.path("code").asText());
        assertEquals(true, body.has("data"));
        assertEquals(true, body.get("data").isNull());
        assertError(get("/test/business/captcha"), 200, "AUTH_1007");
    }

    private JsonNode assertError(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                                 int status, String code) throws Exception {
        var response = mockMvc.perform(request).andReturn().getResponse();
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertEquals(status, response.getStatus());
        assertEquals(code, body.path("code").asText());
        assertEquals(true, body.get("data").isNull());
        assertEquals(false, body.path("message").asText().contains("secret"));
        return body;
    }

    @RestController
    static class TestController {

        @GetMapping("/test/business/{type}")
        void business(@org.springframework.web.bind.annotation.PathVariable String type) {
            switch (type) {
                case "captcha" -> BusinessAssert.fail(AuthErrorCode.CAPTCHA_INVALID, "验证码错误");
                case "password" ->
                        throw new org.springframework.security.authentication.BadCredentialsException("secret detail");
                case "limit" -> throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS);
                case "duplicate" -> throw new DuplicateKeyException("secret SQL and constraint name");
                case "large" -> throw new org.springframework.web.multipart.MaxUploadSizeExceededException(1L);
                case "token" -> throw new io.jsonwebtoken.MalformedJwtException("secret parser detail");
                case "disabled" ->
                        throw new org.springframework.security.authentication.DisabledException("secret detail");
                case "auth-service" ->
                        throw new org.springframework.security.authentication.AuthenticationServiceException("secret backend failure");
                case "unknown-user" ->
                        throw new org.springframework.security.core.userdetails.UsernameNotFoundException("secret username");
                case "unauthenticated" ->
                        throw new org.springframework.security.authentication.InsufficientAuthenticationException("secret detail");
                case "integrity" -> throw new DataIntegrityViolationException("secret SQL and internal schema");
                case "missing" -> throw new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
                case "exists" -> throw new BusinessException(CommonErrorCode.DATA_ALREADY_EXISTS);
                case "unregistered" -> throw new BusinessException(new fun.rooe.admin.error.ErrorCode() {
                    public String getCode() {
                        return "CUSTOM_1001";
                    }

                    public String getMessage() {
                        return "自定义业务错误";
                    }
                });
                default -> throw new IllegalStateException("secret internal detail");
            }
        }

        @GetMapping("/test/framework/{status}")
        void framework(@org.springframework.web.bind.annotation.PathVariable int status) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatusCode.valueOf(status), "secret internal reason");
        }

        @GetMapping("/test/provider/{failure}")
        void provider(@org.springframework.web.bind.annotation.PathVariable String failure) {
            var provider = new DaoAuthenticationProvider(username -> {
                if ("disabled".equals(failure)) {
                    throw new DisabledException("secret account state");
                }
                if ("credentials".equals(failure)) {
                    throw new BadCredentialsException("secret credentials");
                }
                throw new IllegalStateException("secret backend failure");
            });
            provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("admin", "password"));
        }

        @PostMapping("/test/upload")
        void upload(@org.springframework.web.bind.annotation.RequestPart
                    org.springframework.web.multipart.MultipartFile file) {
        }

        @GetMapping("/test/parameter")
        Map<String, Integer> parameter(@RequestParam int number) {
            return Map.of("number", number);
        }

        @PostMapping(value = "/test/json", consumes = MediaType.APPLICATION_JSON_VALUE)
        Map<String, Object> json(@RequestBody Map<String, Object> body) {
            return body;
        }

        @GetMapping("/test/data")
        Map<String, String> data() {
            return Map.of("name", "admin");
        }

        @GetMapping("/test/void")
        void empty() {
        }

        @GetMapping("/test/download")
        void download(HttpServletResponse response) throws IOException {
            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
            response.getWriter().write("file");
        }

        @GetMapping("/test/error")
        void error() {
            throw new BadRequestException(422, "invalid request");
        }

        @GetMapping("/test/unexpected-error")
        void unexpectedError() {
            throw new IllegalStateException("unexpected error");
        }
    }
}
