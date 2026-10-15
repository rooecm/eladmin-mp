package fun.rooe.admin.modules.system.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import fun.rooe.admin.modules.system.domain.entity.User;
import fun.rooe.admin.modules.system.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
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
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test"), User.class);
        userMapper = mock(UserMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new UserCredentialServiceImpl(userMapper, passwordEncoder);
    }

    @Test
    void matchesCurrentDatabaseHash() {
        when(userMapper.selectOne(any())).thenReturn(userWithPassword("stored-hash"));
        when(passwordEncoder.matches("raw-password", "stored-hash")).thenReturn(true);

        assertTrue(service.matches("admin", "raw-password"));

        verify(passwordEncoder).matches("raw-password", "stored-hash");
    }

    @Test
    void rejectsPasswordThatDoesNotMatchCurrentHash() {
        when(userMapper.selectOne(any())).thenReturn(userWithPassword("stored-hash"));
        when(passwordEncoder.matches("wrong-password", "stored-hash")).thenReturn(false);

        assertFalse(service.matches("admin", "wrong-password"));

        verify(passwordEncoder).matches("wrong-password", "stored-hash");
    }

    @Test
    void rejectsUnknownUserWithoutCallingEncoder() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertFalse(service.matches("missing", "raw-password"));

        verifyNoInteractions(passwordEncoder);
    }

    private User userWithPassword(String password) {
        User user = new User();
        user.setPassword(password);
        return user;
    }
}
