# Rooe Starter

`rooe-starter` is an independent Maven project alongside `rooe-admin`. It provides
the parent POM and dependency management for Spring Boot starters. The repository
root POM aggregates both projects; neither project inherits from that aggregator.
`rooe-admin` consumes the observability starter through Maven coordinates.

## Modules

- `rooe-starter`: reactor and parent POM for starter modules. It inherits Spring
  Boot 3.5.16 dependency and plugin management and targets Java 17.
- `rooe-starter-dependencies`: standalone importable BOM, used by both the starter
  parent and consumer applications. Put Rooe starter and shared third-party
  versions here. It does not export Spring Boot dependency management, so each
  application retains its own Spring Boot baseline.
- `rooe-starter-support`: shared infrastructure for starter authors, including a
  reusable environment post-processor for loading low-priority YAML defaults.
- `rooe-observability-spring-boot-starter`: Actuator, Micrometer's OpenTelemetry
  bridge, Logbook, and the auto-configuration for Servlet trace response headers,
  trace-aware JSON web logging, and overridable Logbook defaults in one module.

The BOM only manages versions; importing it does not add runtime dependencies.
See [the observability starter](rooe-observability-spring-boot-starter/README.md)
for configuration, defaults, and extension points.

## Adding a starter

Create `rooe-starter-<feature>/pom.xml`, register its directory in the root
`<modules>` list, and make it inherit the project parent:

```xml
<parent>
    <groupId>fun.rooe</groupId>
    <artifactId>rooe-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</parent>
<artifactId>rooe-starter-example</artifactId>
```

Declare only the dependencies the starter actually needs. Put any dependency
versions not supplied by Spring Boot in the BOM's `dependencyManagement`.
When a starter is ready for external use, manage its artifact version in the BOM too.
Auto-configuration belongs in the feature module and must be registered in
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

For starter-specific default YAML configuration, use the shared
[starter support module](rooe-starter-support/README.md). Each feature starter
only needs the support dependency and `src/main/resources/<module-name>-default.yml`;
support automatically scans `classpath*:*-default.yml`. No feature-specific
processor or registration is needed. Custom locations remain an optional extension.

Other Maven projects can import the BOM without inheriting the parent:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>fun.rooe</groupId>
            <artifactId>rooe-starter-dependencies</artifactId>
            <version>1.0.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

From the repository root, build admin and its starter dependencies together:

```shell
mvn -pl rooe-admin/admin-bootstrap -am clean package
```

This reactor build requires no prior `install` or remote publication of Rooe
artifacts. Import the root `pom.xml` in the IDE to resolve both projects together.
Building `rooe-admin` alone instead requires `mvn -f rooe-starter/pom.xml install`
first (or published artifacts).

Build this project independently with `mvn -f rooe-starter/pom.xml verify` using
JDK 17 or later.
