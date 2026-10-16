package fun.rooe.starter.support.env;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Loads a starter's static YAML defaults below existing environment property sources.
 */
public abstract class AbstractYamlDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private final String sourceName;
    private final String resourcePath;

    protected AbstractYamlDefaultsEnvironmentPostProcessor(String sourceName, String resourcePath) {
        Assert.hasText(sourceName, "Default property source name must not be blank");
        Assert.hasText(resourcePath, "Default YAML resource path must not be blank");
        this.sourceName = sourceName;
        this.resourcePath = resourcePath;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            Resource resource = getDefaultsResource();
            if (!resource.exists()) {
                throw new FileNotFoundException(resourcePath);
            }
            YamlDefaultsLoader.load(sourceName, resource, environment);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load default YAML configuration: " + resourcePath, exception);
        }
    }

    private Resource getDefaultsResource() throws IOException {
        Class<?> owner = getClass();
        var classResource = owner.getResource(ClassUtils.getClassFileName(owner));
        if (classResource == null) {
            throw new FileNotFoundException("Cannot locate processor class resource: " + owner.getName());
        }
        int packageDepth = owner.getPackageName().isEmpty() ? 0 : owner.getPackageName().split("\\.").length;
        String path = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        // Resolve within the processor's own classpath root, including its containing JAR.
        var resource = new UrlResource(classResource);
        resource.setUseCaches(false);
        return resource.createRelative("../".repeat(packageDepth) + path);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
