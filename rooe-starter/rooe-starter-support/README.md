# Rooe Starter Support

Shared infrastructure for Rooe starter authors. This module has no business
configuration, tracing or web logging dependencies. Its registered environment
post-processor automatically discovers static YAML defaults.

## Default YAML Configuration

Add the managed dependency to a feature starter:

```xml

<dependency>
    <groupId>fun.rooe</groupId>
    <artifactId>rooe-starter-support</artifactId>
</dependency>
```

Keep the starter's YAML at `src/main/resources/<module-name>-default.yml`. For
example, observability uses `rooe-observability-default.yml`.
That is all the feature starter needs: support registers its own
`EnvironmentPostProcessor` in
`META-INF/spring.factories` and scans `classpath*:*-default.yml` at startup.
Every matching file is loaded, including files in different starter JARs.
There is no subclass or registration required for this default convention.

## Custom Locations

For a nonstandard location, use the optional abstract base and declare a unique
property-source name. Keep this custom file outside the automatically scanned
root `*-default.yml` pattern to avoid loading the same file twice:

```java
package fun.rooe.example.autoconfigure;

import fun.rooe.starter.support.env.AbstractYamlDefaultsEnvironmentPostProcessor;

public class ExampleEnvironmentPostProcessor extends AbstractYamlDefaultsEnvironmentPostProcessor {

    public ExampleEnvironmentPostProcessor() {
        super("rooeExampleDefaults", "config/example-defaults.yml");
    }
}
```

The path is relative to that starter's resources root. Register this optional
custom-location subclass in that starter's `META-INF/spring.factories`:

```properties
org.springframework.boot.env.EnvironmentPostProcessor=\
fun.rooe.example.autoconfigure.ExampleEnvironmentPostProcessor
```

No application code or component scanning is required for either approach.

## Loading Rules

- Runs after Boot config data processing and appends defaults below existing
  environment property sources. Application YAML, environment variables and
  command-line values override these defaults.
- Later documents in a multi-document YAML file override earlier documents.
- Repeated processing preserves existing property sources without adding
  duplicates or changing their priority.
- Automatic loading uses resource URLs as property-source identities. Each
  module defaults file is loaded independently. Repeated processing adds no duplicates.
  No matching files is valid and does not prevent startup. The scan covers
  classpath roots, not nested directories or unprefixed `default.yml` files.
- Avoid overlapping default keys across starters; automatic loading follows
  resource discovery order, and the first file wins for overlapping keys.
- Custom-location subclasses locate resources relative to their own classpath
  root or containing JAR and must use unique property-source names.
- Invalid YAML fails startup with its resource location. A required custom file
  that is missing also fails startup; automatic scanning skips absent files.
- These are static defaults. Loading does not process `spring.config.import`
  or profile activation directives; put profile-specific values in application
  config data.

The module supplies SnakeYAML at runtime. Versions of Spring Boot and SnakeYAML
are managed by the application's Spring Boot baseline.
