package fun.rooe.admin.modules.system.service.impl;

import fun.rooe.admin.modules.system.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class UserCredentialServiceImplTest {
    private UserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private UserCredentialServiceImpl service;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new UserCredentialServiceImpl(userMapper, passwordEncoder);
    }

    @Test
    void matchesCurrentDatabaseHash() {
        when(userMapper.findPasswordHashByUsername("admin")).thenReturn("stored-hash");
        when(passwordEncoder.matches("raw-password", "stored-hash")).thenReturn(true);

        assertTrue(service.matches("admin", "raw-password"));

        verify(passwordEncoder).matches("raw-password", "stored-hash");
    }

    @Test
    void rejectsPasswordThatDoesNotMatchCurrentHash() {
        when(userMapper.findPasswordHashByUsername("admin")).thenReturn("stored-hash");
        when(passwordEncoder.matches("wrong-password", "stored-hash")).thenReturn(false);

        assertFalse(service.matches("admin", "wrong-password"));

        verify(passwordEncoder).matches("wrong-password", "stored-hash");
    }

    @Test
    void rejectsUnknownUserWithoutCallingEncoder() {
        when(userMapper.findPasswordHashByUsername("missing")).thenReturn(null);

        assertFalse(service.matches("missing", "raw-password"));

        verifyNoInteractions(passwordEncoder);
    }
}
