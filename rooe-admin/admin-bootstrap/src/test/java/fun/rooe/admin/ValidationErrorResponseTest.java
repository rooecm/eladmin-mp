package fun.rooe.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.rooe.admin.framework.advice.ApiResultResponseAdvice;
import fun.rooe.admin.framework.advice.GlobalExceptionAdvice;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class ValidationErrorResponseTest {
    @Test
    void controllerMethodValidationKeepsTheConstraintMessage() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new ApiResultResponseAdvice(), new GlobalExceptionAdvice()).build();
        var response = mvc.perform(get("/validation/parameter").param("count", "0"))
                .andReturn().getResponse();
        var body = new ObjectMapper().readTree(response.getContentAsByteArray());
        assertEquals(400, response.getStatus());
        assertEquals("COMMON_1001", body.path("code").asText());
        assertEquals("数量至少为1", body.path("message").asText());
    }

    @Test
    void invalidControllerReturnValuesAreReportedAsSystemFailures() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new ApiResultResponseAdvice(), new GlobalExceptionAdvice()).build();
        var response = mvc.perform(get("/validation/return")).andReturn().getResponse();
        var body = new ObjectMapper().readTree(response.getContentAsByteArray());
        assertEquals(500, response.getStatus());
        assertEquals("SYS_1001", body.path("code").asText());
        assertEquals("系统内部异常，请稍后重试", body.path("message").asText());
    }

    @Test
    void beanValidationReturnsAFieldMessageAndTheCommonArgumentCode() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new ApiResultResponseAdvice(), new GlobalExceptionAdvice()).build();
        var response = mvc.perform(post("/validation").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse();
        var body = new ObjectMapper().readTree(response.getContentAsByteArray());
        assertEquals(400, response.getStatus());
        assertEquals("COMMON_1001", body.path("code").asText());
        assertEquals("name: 名称不能为空", body.path("message").asText());
        assertTrue(body.get("data").isNull());
    }

    @RestController
    static class TestController {
        @PostMapping("/validation")
        public Request create(@Valid @RequestBody Request request) {
            return request;
        }

        @GetMapping("/validation/parameter")
        public int parameter(@RequestParam @Min(value = 1, message = "数量至少为1") int count) {
            return count;
        }

        @NotBlank(message = "secret internal return constraint")
        @GetMapping("/validation/return")
        public String invalidReturn() {
            return "";
        }
    }

    record Request(@NotBlank(message = "名称不能为空") String name) {
    }
}
