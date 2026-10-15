package fun.rooe.admin.modules.security.dto;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import fun.rooe.admin.modules.system.domain.dto.UserLoginDto;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class JwtUserDtoSerializationTest {

    @Test
    void cachedPrincipalRoundTripDoesNotContainPassword() {
        UserLoginDto user = new UserLoginDto();
        user.setId(1L);
        user.setUsername("admin");
        user.setEnabled(true);
        JwtUserDto principal =
                new JwtUserDto(user, Collections.emptyList(), Collections.emptyList());

        String json = JSON.toJSONString(principal, JSONWriter.Feature.WriteClassName);
        JwtUserDto restored = JSON.parseObject(json, JwtUserDto.class);

        assertFalse(json.contains("password"));
        assertEquals("admin", restored.getUsername());
        assertNull(restored.getPassword());
    }
}
