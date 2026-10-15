package fun.rooe.admin.framework.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.rooe.admin.exception.BadRequestException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiResultResponseAdviceTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
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

        assertEquals(422, response.getStatus());
        assertEquals(false, body.has("status"));
        assertEquals("422", body.get("code").asText());
        assertEquals("invalid request", body.get("message").asText());
        assertEquals(true, body.get("data").isNull());
    }

    @Test
    void mapsUnexpectedExceptionToInternalServerError() throws Exception {
        var response = mockMvc.perform(get("/test/unexpected-error")).andReturn().getResponse();
        JsonNode body = objectMapper.readTree(response.getContentAsString());

        assertEquals(500, response.getStatus());
        assertEquals(false, body.has("status"));
        assertEquals("500", body.get("code").asText());
        assertEquals("unexpected error", body.get("message").asText());
    }

    @RestController
    static class TestController {

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
