package fun.rooe.utils;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Set;

import static fun.rooe.utils.StringUtils.getIp;
import static fun.rooe.utils.StringUtils.getWeekDay;
import static fun.rooe.utils.StringUtils.toCamelCase;
import static fun.rooe.utils.StringUtils.toCapitalizeCamelCase;
import static fun.rooe.utils.StringUtils.toUnderScoreCase;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StringUtilsTest {

    @Test
    public void testToCamelCase() {
        assertNull(toCamelCase(null));
    }

    @Test
    public void testToCapitalizeCamelCase() {
        assertNull(StringUtils.toCapitalizeCamelCase(null));
        assertEquals("HelloWorld", toCapitalizeCamelCase("hello_world"));
    }

    @Test
    public void testToUnderScoreCase() {
        assertNull(StringUtils.toUnderScoreCase(null));
        assertEquals("hello_world", toUnderScoreCase("helloWorld"));
        assertEquals("\u0000\u0000", toUnderScoreCase("\u0000\u0000"));
        assertEquals("\u0000_a", toUnderScoreCase("\u0000A"));
    }

    @Test
    public void testGetWeekDay() {
        assertTrue(Set.of("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").contains(getWeekDay()));
    }

    @Test
    public void testGetIP() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("x-forwarded-for", "203.0.113.10, 198.51.100.20");
        assertEquals("203.0.113.10", getIp(request));
    }
}
