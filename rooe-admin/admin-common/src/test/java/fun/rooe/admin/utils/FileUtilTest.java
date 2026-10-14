package fun.rooe.admin.utils;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static fun.rooe.admin.utils.FileUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileUtilTest {

    @Test
    public void testUpload() throws Exception {
        File dir = Files.createTempDirectory("file-util-test").toFile();
        File file = upload(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)), "测试 文件.txt", dir.getPath() + File.separator);
        assertNotNull(file);
        assertTrue(file.exists());
        assertTrue(file.getName().endsWith(".txt"));
    }

    @Test
    public void testGetExtensionName() {
        assertEquals("foo", getExtensionName("foo"));
        assertEquals("exe", getExtensionName("bar.exe"));
    }

    @Test
    public void testGetFileNameNoEx() {
        assertEquals("foo", getFileNameNoEx("foo"));
        assertEquals("bar", getFileNameNoEx("bar.txt"));
    }

    @Test
    public void testGetSize() {
        assertEquals("1000B", getSize(1000));
        assertEquals("1.00KB", getSize(1024));
        assertEquals("1.00MB", getSize(1048576));
        assertEquals("1.00GB", getSize(1073741824));
    }
}
