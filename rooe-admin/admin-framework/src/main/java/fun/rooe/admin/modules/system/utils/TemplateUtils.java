package fun.rooe.admin.modules.system.utils;

import freemarker.template.Configuration;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

public final class TemplateUtils {

    private static final Configuration CONFIGURATION = new Configuration(Configuration.VERSION_2_3_32);

    static {
        CONFIGURATION.setClassForTemplateLoading(TemplateUtils.class, "/template");
        CONFIGURATION.setDefaultEncoding(StandardCharsets.UTF_8.name());
    }

    private TemplateUtils() {
    }

    public static String render(String templateName, Object data) {
        try (StringWriter writer = new StringWriter()) {
            CONFIGURATION.getTemplate(templateName).process(data, writer);
            return writer.toString();
        } catch (IOException | TemplateException e) {
            throw new IllegalStateException(e);
        }
    }
}
