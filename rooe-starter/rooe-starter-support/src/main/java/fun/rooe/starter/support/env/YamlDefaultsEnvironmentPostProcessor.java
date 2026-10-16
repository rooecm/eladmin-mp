package fun.rooe.starter.support.env;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.ClassUtils;

import java.io.IOException;

/**
 * Automatically loads classpath root module-name-default.yml files as low-priority defaults.
 */
public class YamlDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        var classLoader = application != null ? application.getClassLoader() : ClassUtils.getDefaultClassLoader();
        var resolver = new PathMatchingResourcePatternResolver(classLoader);
        resolver.setUseCaches(false);
        try {
            for (var resource : resolver.getResources("classpath*:*-default.yml")) {
                if (resource instanceof UrlResource urlResource) {
                    urlResource.setUseCaches(false);
                }
                String sourceName = "rooeDefaults [" + resource.getURL().toExternalForm() + "]";
                try {
                    YamlDefaultsLoader.load(sourceName, resource, environment);
                } catch (IOException | RuntimeException exception) {
                    throw new IllegalStateException("Cannot load default YAML configuration: "
                            + resource.getDescription(), exception);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot scan classpath *-default.yml resources", exception);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
