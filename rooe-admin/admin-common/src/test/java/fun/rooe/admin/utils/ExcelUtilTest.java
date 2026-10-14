package fun.rooe.admin.utils;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static fun.rooe.admin.utils.ExcelUtil.write;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExcelUtilTest {

    @Test
    public void testWrite() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        write(List.of(Map.of("name", "=test", "age", 18)), outputStream);
        assertTrue(outputStream.size() > 0);
    }
}
