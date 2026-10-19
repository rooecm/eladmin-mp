package fun.rooe.admin.infrastructure.security;

import fun.rooe.admin.infrastructure.config.mybatis.CustomP6SpyLogger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SqlCredentialRedactionTest {
    @Test
    void neverLogsPasswordHashesOrBoundValues() {
        var logger = new CustomP6SpyLogger();
        String output = logger.formatMessage(1, "now", 1, "statement",
                "UPDATE sys_user SET password = ? WHERE id = ?",
                "UPDATE sys_user SET password = 'hash-secret' WHERE id = 2", "");
        assertFalse(output.contains("hash-secret"));
        assertTrue(output.contains("credential SQL omitted"));
        output = logger.formatMessage(1, "now", 1, "statement", "SELECT * FROM sys_user WHERE username = ?",
                "SELECT * FROM sys_user WHERE username = 'bound-secret'", "");
        assertFalse(output.contains("bound-secret"));
        assertTrue(output.contains("username = ?"));
    }
}
