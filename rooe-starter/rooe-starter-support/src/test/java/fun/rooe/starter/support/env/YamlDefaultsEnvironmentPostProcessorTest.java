package fun.rooe.starter.support.env;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlDefaultsEnvironmentPostProcessorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void activatesOnSpringApplicationStartupWithoutFeatureStarterRegistration() {
        var application = new SpringApplication(TestApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);
        try (var context = application.run()) {
            assertThat(context.getEnvironment().getProperty("default.enabled")).isEqualTo("true");
            assertThat(context.getEnvironment().getPropertySources().stream()
                    .filter(source -> source.getName().startsWith("rooeDefaults ["))).hasSize(1);
        }
    }

    @Test
    void discoversModuleDefaultsWithoutProcessorClassesInStarterJars() throws Exception {
        Path first = starterJar("first.jar", "first-default.yml", "first:\n  enabled: true\n  paths: [/health]");
        Path second = starterJar("second.jar", "second-default.yml", "second.enabled: true");
        var environment = new MockEnvironment();
        environment.getPropertySources().addLast(new MapPropertySource("application",
                Map.of("first.enabled", "false")));
        try (var loader = isolatedClassLoader(first, second)) {
            process(environment, loader);
            assertThat(environment.getProperty("first.enabled")).isEqualTo("false");
            assertThat(environment.getProperty("first.paths[0]")).isEqualTo("/health");
            assertThat(environment.getProperty("second.enabled")).isEqualTo("true");
            assertThat(environment.getProperty("default.enabled")).isNull();
            var sources = environment.getPropertySources().stream().toList();
            assertThat(sources).hasSize(4);

            process(environment, loader);
            assertThat(environment.getPropertySources().stream().toList()).containsExactlyElementsOf(sources);
        }
    }

    @Test
    void laterYamlDocumentsOverrideEarlierOnesBelowApplicationConfiguration() throws Exception {
        Path jar = starterJar("multi.jar", "multi-default.yml",
                "multi.mode: first\nmulti.first: kept\n---\nmulti.mode: last\nmulti.last: kept");
        var environment = new MockEnvironment().withProperty("multi.first", "application");
        try (var loader = isolatedClassLoader(jar)) {
            process(environment, loader);
            assertThat(environment.getProperty("multi.mode")).isEqualTo("last");
            assertThat(environment.getProperty("multi.first")).isEqualTo("application");
            assertThat(environment.getProperty("multi.last")).isEqualTo("kept");
            var sources = environment.getPropertySources().stream().toList();
            process(environment, loader);
            assertThat(environment.getPropertySources().stream().toList()).containsExactlyElementsOf(sources);
        }
    }

    @Test
    void supportsExplodedClasspathRoots() throws Exception {
        Files.writeString(temporaryDirectory.resolve("directory-default.yml"), "directory.enabled: true");
        try (var loader = isolatedClassLoader(temporaryDirectory)) {
            var environment = new MockEnvironment();
            process(environment, loader);
            assertThat(environment.getProperty("directory.enabled")).isEqualTo("true");
        }
    }

    @Test
    void startsNormallyWhenNoDefaultFilesExist() throws Exception {
        var environment = new MockEnvironment();
        var sources = environment.getPropertySources().stream().toList();
        try (var loader = isolatedClassLoader()) {
            process(environment, loader);
        }
        assertThat(environment.getPropertySources().stream().toList()).containsExactlyElementsOf(sources);
    }

    @Test
    void scansOnlyModuleNamedFilesAtClasspathRoot() throws Exception {
        Path jar = starterJar("nested.jar", "config/nested-default.yml", "nested.enabled: true");
        Path plain = starterJar("plain.jar", "default.yml", "plain.enabled: true");
        try (var loader = isolatedClassLoader(jar, plain)) {
            var environment = new MockEnvironment();
            process(environment, loader);
            assertThat(environment.getProperty("nested.enabled")).isNull();
            assertThat(environment.getProperty("plain.enabled")).isNull();
        }
    }

    @Test
    void reportsInvalidYamlWithItsResourceLocation() throws Exception {
        Path jar = starterJar("invalid.jar", "invalid-default.yml", "broken: [");
        try (var loader = isolatedClassLoader(jar)) {
            assertThatThrownBy(() -> process(new MockEnvironment(), loader))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("invalid.jar").hasMessageContaining("default.yml");
        }
    }

    @Test
    void runsAfterApplicationConfigData() {
        assertThat(new YamlDefaultsEnvironmentPostProcessor().getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
    }

    private void process(MockEnvironment environment, ClassLoader loader) {
        var application = new SpringApplication(TestApplication.class);
        application.setResourceLoader(new DefaultResourceLoader(loader));
        new YamlDefaultsEnvironmentPostProcessor().postProcessEnvironment(environment, application);
    }

    private URLClassLoader isolatedClassLoader(Path... roots) throws IOException {
        var urls = new URL[roots.length];
        for (int index = 0; index < roots.length; index++) {
            urls[index] = roots[index].toUri().toURL();
        }
        return new URLClassLoader(urls, null);
    }

    private Path starterJar(String fileName, String resourcePath, String yaml) throws IOException {
        Path path = temporaryDirectory.resolve(fileName);
        try (var output = new JarOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new JarEntry(resourcePath));
            output.write(yaml.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }

    @Configuration(proxyBeanMethods = false)
    static class TestApplication {
    }
}
