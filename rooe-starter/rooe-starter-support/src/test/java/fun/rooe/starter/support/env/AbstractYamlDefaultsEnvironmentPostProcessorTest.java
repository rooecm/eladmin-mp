package fun.rooe.starter.support.env;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.io.FileNotFoundException;
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

class AbstractYamlDefaultsEnvironmentPostProcessorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsNamedYamlFromResourcesRoot() {
        var environment = new MockEnvironment();
        new FixtureDefaultsEnvironmentPostProcessor("rootDefaults", "rooe-starter-support-default.yml")
                .postProcessEnvironment(environment, null);
        assertThat(environment.getProperty("default.enabled")).isEqualTo("true");
        assertThat(environment.getPropertySources().contains("rootDefaults")).isTrue();
    }

    @Test
    void keepsSameNamedDefaultFilesIsolatedAcrossStarterJars() throws Exception {
        var environment = new MockEnvironment();
        Path first = starterJar("first.jar", "fixture-default.yml", "first.enabled: true");
        Path second = starterJar("second.jar", "fixture-default.yml", "second.enabled: true");
        try (var firstLoader = new StarterClassLoader(first); var secondLoader = new StarterClassLoader(second)) {
            jarProcessor(firstLoader, "firstJar", null).postProcessEnvironment(environment, null);
            jarProcessor(secondLoader, "secondJar", null).postProcessEnvironment(environment, null);
        }
        assertThat(environment.getProperty("first.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("second.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("default.enabled")).isNull();
    }

    @Test
    void supportsCustomLocationsInsideStarterJars() throws Exception {
        Path jar = starterJar("custom.jar", "config/custom.yml", "custom.enabled: true");
        var environment = new MockEnvironment();
        try (var loader = new StarterClassLoader(jar)) {
            jarProcessor(loader, "customJar", "config/custom.yml").postProcessEnvironment(environment, null);
        }
        assertThat(environment.getProperty("custom.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("default.enabled")).isNull();
    }

    @Test
    void doesNotFallBackToAnotherClasspathDefaultWhenOwnFileIsMissing() throws Exception {
        Path jar = starterJar("missing.jar", "config/other.yml", "other.enabled: true");
        try (var loader = new StarterClassLoader(jar)) {
            var processor = jarProcessor(loader, "missingJar", null);
            assertThatThrownBy(() -> processor.postProcessEnvironment(new MockEnvironment(), null))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("fixture-default.yml")
                    .hasCauseInstanceOf(FileNotFoundException.class);
        }
    }

    @Test
    void loadsNestedYamlAndListsWithoutOverridingApplicationValues() {
        var environment = new MockEnvironment();
        environment.getPropertySources().addLast(new MapPropertySource("application",
                Map.of("first.limit", "99", "first.enabled", "false")));

        processor("firstDefaults", "defaults/first.yml").postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("first.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("first.limit")).isEqualTo("99");
        assertThat(environment.getProperty("first.mode")).isEqualTo("default");
        assertThat(environment.getProperty("first.paths[0]")).isEqualTo("/health");
        assertThat(environment.getPropertySources().stream().map(source -> source.getName()).toList())
                .endsWith("application", "firstDefaults");
    }

    @Test
    void allowsMultipleStartersToContributeIndependentDefaults() {
        var environment = new MockEnvironment();
        processor("firstDefaults", "defaults/first.yml").postProcessEnvironment(environment, null);
        processor("secondDefaults", "defaults/second.yml").postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("first.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("second.enabled")).isEqualTo("true");
        assertThat(environment.getPropertySources().contains("firstDefaults")).isTrue();
        assertThat(environment.getPropertySources().contains("secondDefaults")).isTrue();
    }

    @Test
    void preservesExistingSourceOnRepeatedProcessing() {
        var environment = new MockEnvironment();
        var processor = processor("firstDefaults", "defaults/first.yml");
        processor.postProcessEnvironment(environment, null);
        var original = environment.getPropertySources().get("firstDefaults");
        int count = environment.getPropertySources().size();

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getPropertySources().get("firstDefaults")).isSameAs(original);
        assertThat(environment.getPropertySources().size()).isEqualTo(count);
    }

    @Test
    void laterYamlDocumentsOverrideEarlierDocumentsAndRemainIdempotent() {
        var environment = new MockEnvironment().withProperty("multi.application", "host");
        var processor = processor("multiDefaults", "defaults/multiple.yml");
        processor.postProcessEnvironment(environment, null);
        var sources = environment.getPropertySources().stream().toList();

        assertThat(environment.getProperty("multi.mode")).isEqualTo("last");
        assertThat(environment.getProperty("multi.first")).isEqualTo("kept");
        assertThat(environment.getProperty("multi.last")).isEqualTo("kept");
        assertThat(environment.getProperty("multi.application")).isEqualTo("host");

        processor.postProcessEnvironment(environment, null);
        assertThat(environment.getPropertySources().stream().toList()).containsExactlyElementsOf(sources);
    }

    @Test
    void reportsMissingRequiredResourceWithItsPath() {
        assertThatThrownBy(() -> processor("missingDefaults", "defaults/missing.yml")
                .postProcessEnvironment(new MockEnvironment(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("defaults/missing.yml")
                .hasCauseInstanceOf(FileNotFoundException.class);
    }

    @Test
    void runsAfterConfigDataAndRejectsBlankResourceDefinitions() {
        assertThat(processor("firstDefaults", "defaults/first.yml").getOrder())
                .isEqualTo(Ordered.LOWEST_PRECEDENCE);
        assertThatThrownBy(() -> processor(" ", "defaults/first.yml"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> processor("firstDefaults", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private AbstractYamlDefaultsEnvironmentPostProcessor processor(String name, String path) {
        return new AbstractYamlDefaultsEnvironmentPostProcessor(name, path) {
        };
    }

    private Path starterJar(String fileName, String resourcePath, String yaml) throws IOException {
        Path path = temporaryDirectory.resolve(fileName);
        String classPath = FixtureDefaultsEnvironmentPostProcessor.class.getName().replace('.', '/') + ".class";
        try (var output = new JarOutputStream(Files.newOutputStream(path));
             var bytecode = getClass().getClassLoader().getResourceAsStream(classPath)) {
            output.putNextEntry(new JarEntry(classPath));
            bytecode.transferTo(output);
            output.closeEntry();
            output.putNextEntry(new JarEntry(resourcePath));
            output.write(yaml.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }

    private AbstractYamlDefaultsEnvironmentPostProcessor jarProcessor(ClassLoader loader, String sourceName,
                                                                      String resourcePath) throws ReflectiveOperationException {
        Class<?> type = loader.loadClass(FixtureDefaultsEnvironmentPostProcessor.class.getName());
        return (AbstractYamlDefaultsEnvironmentPostProcessor) type.getConstructor(String.class, String.class)
                .newInstance(sourceName, resourcePath == null ? "fixture-default.yml" : resourcePath);
    }

    private static class StarterClassLoader extends URLClassLoader {

        StarterClassLoader(Path jar) throws IOException {
            super(new URL[]{jar.toUri().toURL()}, FixtureDefaultsEnvironmentPostProcessor.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(FixtureDefaultsEnvironmentPostProcessor.class.getName())) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> type = findLoadedClass(name);
                    if (type == null) {
                        type = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(type);
                    }
                    return type;
                }
            }
            return super.loadClass(name, resolve);
        }

        @Override
        public URL getResource(String name) {
            URL local = findResource(name);
            return local == null ? super.getResource(name) : local;
        }
    }
}
