# Rooe Observability Starter

Reusable Servlet observability for Java 17 and Spring Boot 3.5.x. `rooe-admin`
declares this starter in `admin-framework`; `admin-bootstrap` receives it transitively.

This feature module contains dependency aggregation, auto-configuration classes,
default YAML configuration, and tests. It uses `rooe-starter-support` for shared
default YAML loading. No separate Rooe autoconfigure artifact is required.

## Install

Within this repository, build admin with its starter dependencies using JDK 17 or later:

```shell
mvn -pl rooe-admin/admin-bootstrap -am clean package
```

The root POM aggregates both projects, so Rooe artifacts need no prior local
installation or remote publication. For applications outside this reactor,
build and install the independent starter project first:

```shell
mvn -f rooe-starter/pom.xml clean install
```

An application can import `fun.rooe:rooe-starter-dependencies:1.0.0-SNAPSHOT`
as a Maven BOM, then add:

```xml
<dependency>
    <groupId>fun.rooe</groupId>
    <artifactId>rooe-observability-spring-boot-starter</artifactId>
</dependency>
```

Without the BOM, specify the starter version explicitly. The application supplies
its Servlet web stack, normally `spring-boot-starter-web`. Spring Security is
optional and is not introduced by this starter. WebFlux is not supported.

## Behavior

- Spring Boot and Micrometer generate the trace ID and honor W3C `traceparent`
  propagation. This starter does not generate a second ID or trust an incoming
  `traceId` header. Boot also puts trace/span IDs in MDC during active spans.
- HTTP responses contain `traceId`, including security rejections, handled
  exceptions, container error dispatches, asynchronous responses, and downloads.
  No header is invented when no trace exists.
- A Servlet filter runs after Boot's HTTP observation filter and before Spring
  Security. When Security is present, the default Logbook security filter is
  registered after tracing and before Security, so rejected requests are traced.
- CORS exposure appends the trace header while preserving application-defined
  exposed headers. Allowed origins, credentials, methods, and other CORS rules
  remain application decisions.
- Logbook emits JSON request/response logs at INFO under
  `org.zalando.logbook.Logbook`. Both JSON records include a top-level `traceId`
  from the active span. Logbook's own `correlation` still pairs request/response
  records. No application `logback.xml` or TRACE logger configuration is required.

This starter ships no generic `application.yml` or `logback.xml`.
`rooe-starter-support` automatically scans `classpath*:*-default.yml`, including
this starter's root `rooe-observability-default.yml`, and loads
them below existing environment property sources. No observability-specific
environment post-processor registration is needed. Application configuration,
environment variables, and command-line arguments take precedence.

## Configuration

These defaults work without application configuration:

```yaml
rooe:
  observability:
    trace-header:
      enabled: true
      name: traceId
      expose: true

management:
  tracing:
    enabled: true

logbook:
  format:
    style: json
  write:
    max-body-size: 4096
```

Use `rooe.observability.trace-header.name: X-Trace-Id` to change the response
header, or `expose: false` to disable automatic CORS exposure. Header names must
be valid HTTP tokens.

Disable response headers and both Servlet logging filters independently:

```yaml
rooe:
  observability:
    trace-header:
      enabled: false
logbook:
  filter:
    enabled: false
  secure-filter:
    enabled: false
```

Tracing keeps using the native `management.tracing.*` configuration, including
`management.tracing.enabled` and `management.tracing.sampling.probability`.
Boot's default sampling probability is retained; unsampled requests still get
trace IDs. An exporter is not included. For distributed trace collection, the
application adds its exporter and configures the corresponding Boot endpoint.

Logbook keeps its native `logbook.*` configuration:

| Setting                 | Default                                                                                                 |
|-------------------------|---------------------------------------------------------------------------------------------------------|
| Excluded paths          | `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `/webjars/**`, `/favicon.ico`                      |
| Masked headers          | `Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie`                                          |
| Masked query parameters | `access_token`, `refresh_token`, `token`, `password`, `oldPass`, `newPass`                              |
| Masked JSON fields      | `password`, `oldPass`, `newPass`, `confirmPassword`, `token`, `access_token`, `refresh_token`, `secret` |

The replacement is Logbook's `XXX`; nested JSON fields are masked. Actual
requests and responses are unchanged, and business fields such as `code` remain
visible. Application-specific upload, download, websocket, Druid, and static
resource routes should be added through `logbook.predicate.exclude` as needed.
Logbook properties that bind collections replace the default collection; include
all desired entries when overriding exclusions or masked fields.

The body limit truncates logged content; it is not a maximum request size or a
buffer-memory limit. Exclude large transfers and streaming routes, or configure
Logbook's native body strategy for those applications.

## Extension Points

Application beans of type `HttpLogWriter` and `HttpLogFormatter` replace the
starter's writer and formatter. A non-JSON `logbook.format.style` selects
Logbook's native formatter, which does not add the JSON `traceId` field.
Native `HeaderFilter`, `QueryFilter`, `BodyFilter`, and other Logbook extension
points remain available. A registration bean named `rooeTraceIdFilter` or
`secureLogbookFilter` replaces the corresponding default registration; custom
filters should preserve the observation-before-logging order.

Logbook 3.12.3 and starter artifact versions are managed by the standalone BOM.
Micrometer and OpenTelemetry use Spring Boot dependency management rather than
separate version pins. The Rooe BOM does not export Spring Boot dependency
management. Both the starter tests and admin use the Boot 3.5.16 baseline.

Run `mvn -f rooe-starter/pom.xml verify` for auto-configuration tests and an
embedded-server integration test application with Spring Security. These tests
have no dependency on `rooe-admin`, databases, Redis, or external trace collectors.
