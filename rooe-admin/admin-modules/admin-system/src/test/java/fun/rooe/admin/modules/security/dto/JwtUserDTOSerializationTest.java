package fun.rooe.admin.modules.security.dto;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import fun.rooe.admin.model.UserDataScope;
import fun.rooe.admin.modules.system.domain.dto.UserLoginDTO;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class JwtUserDTOSerializationTest {

    @Test
    void cachedPrincipalRoundTripDoesNotContainPassword() {
        UserLoginDTO user = new UserLoginDTO();
        user.setId(1L);
        user.setUsername("admin");
        user.setEnabled(true);
        JwtUserDTO principal =
                new JwtUserDTO(
                        user,
                        Collections.emptyList(),
                        UserDataScope.Mode.ALL,
                        false,
                        Collections.emptyList());

        String json = JSON.toJSONString(principal, JSONWriter.Feature.WriteClassName);
        JwtUserDTO restored = JSON.parseObject(json, JwtUserDTO.class);

        assertFalse(json.contains("password"));
        assertEquals("admin", restored.getUsername());
        assertNull(restored.getPassword());
        assertEquals(UserDataScope.Mode.ALL, restored.getDataScopeMode());
    }
}
