package fun.rooe.admin.modules.security.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.rooe.admin.model.UserDataScope;
import fun.rooe.admin.modules.security.domain.dto.JwtUserDTO;
import fun.rooe.admin.modules.system.domain.dto.UserLoginDTO;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class JwtUserDTOSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void cachedPrincipalRoundTripDoesNotContainPassword() throws Exception {
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

        String json = objectMapper.writeValueAsString(principal);
        JwtUserDTO restored = objectMapper.readValue(json, JwtUserDTO.class);

        assertFalse(json.contains("password"));
        assertEquals("admin", restored.getUsername());
        assertNull(restored.getPassword());
        assertEquals(UserDataScope.Mode.ALL, restored.getDataScopeMode());
    }
}
