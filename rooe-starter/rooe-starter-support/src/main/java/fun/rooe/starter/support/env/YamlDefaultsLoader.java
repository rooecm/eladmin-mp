package fun.rooe.starter.support.env;

import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.Resource;

import java.io.IOException;

final class YamlDefaultsLoader {

    private YamlDefaultsLoader() {
    }

    static void load(String sourceName, Resource resource, ConfigurableEnvironment environment) throws IOException {
        var sources = new YamlPropertySourceLoader().load(sourceName, resource);
        // Later YAML documents override earlier ones, below all application property sources.
        for (int index = sources.size() - 1; index >= 0; index--) {
            var source = sources.get(index);
            if (!environment.getPropertySources().contains(source.getName())) {
                environment.getPropertySources().addLast(source);
            }
        }
    }
}
