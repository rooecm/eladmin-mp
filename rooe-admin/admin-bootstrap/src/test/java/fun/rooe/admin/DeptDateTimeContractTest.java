package fun.rooe.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.rooe.admin.modules.system.domain.dto.DeptDTO;
import fun.rooe.admin.modules.system.domain.request.DeptQueryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.convert.TypeDescriptor;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeptDateTimeContractTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class));

    @Test
    void deptDtoKeepsTheAgreedJsonFormat() {
        contextRunner.run(context -> {
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            DeptDTO dept = new DeptDTO();
            dept.setCreateTime(LocalDateTime.of(2026, 10, 18, 10, 30, 0));

            JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(dept));

            assertEquals("2026-10-18 10:30:00", json.path("createTime").asText());
        });
    }

    @Test
    void deptQueryRequestBindsTheAgreedDateFormat() throws Exception {
        Field createTime = DeptQueryRequest.class.getDeclaredField("createTime");
        TypeDescriptor elementType = new TypeDescriptor(createTime).getElementTypeDescriptor();

        Object parsed = new ApplicationConversionService().convert("2026-10-18 10:30:00", elementType);

        assertEquals(LocalDateTime.of(2026, 10, 18, 10, 30), parsed);
    }
}
