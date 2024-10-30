blockhead
===

[![Maven Central](https://img.shields.io/maven-central/v/com.io7m.blockhead/com.io7m.blockhead.svg?style=flat-square)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.io7m.blockhead%22)
[![Maven Central (snapshot)](https://img.shields.io/nexus/s/com.io7m.blockhead/com.io7m.blockhead?server=https%3A%2F%2Fs01.oss.sonatype.org&style=flat-square)](https://s01.oss.sonatype.org/content/repositories/snapshots/com/io7m/blockhead/)
[![Codecov](https://img.shields.io/codecov/c/github/io7m-com/blockhead.svg?style=flat-square)](https://codecov.io/gh/io7m-com/blockhead)
![Java Version](https://img.shields.io/badge/21-java?label=java&color=e6c35c)

![com.io7m.blockhead](./src/site/resources/blockhead.jpg?raw=true)

| JVM | Platform | Status |
|-----|----------|--------|
| OpenJDK (Temurin) Current | Linux | [![Build (OpenJDK (Temurin) Current, Linux)](https://img.shields.io/github/actions/workflow/status/io7m-com/blockhead/main.linux.temurin.current.yml)](https://www.github.com/io7m-com/blockhead/actions?query=workflow%3Amain.linux.temurin.current)|
| OpenJDK (Temurin) LTS | Linux | [![Build (OpenJDK (Temurin) LTS, Linux)](https://img.shields.io/github/actions/workflow/status/io7m-com/blockhead/main.linux.temurin.lts.yml)](https://www.github.com/io7m-com/blockhead/actions?query=workflow%3Amain.linux.temurin.lts)|
| OpenJDK (Temurin) Current | Windows | [![Build (OpenJDK (Temurin) Current, Windows)](https://img.shields.io/github/actions/workflow/status/io7m-com/blockhead/main.windows.temurin.current.yml)](https://www.github.com/io7m-com/blockhead/actions?query=workflow%3Amain.windows.temurin.current)|
| OpenJDK (Temurin) LTS | Windows | [![Build (OpenJDK (Temurin) LTS, Windows)](https://img.shields.io/github/actions/workflow/status/io7m-com/blockhead/main.windows.temurin.lts.yml)](https://www.github.com/io7m-com/blockhead/actions?query=workflow%3Amain.windows.temurin.lts)|

## blockhead

The `blockhead` package provides a trivial command-line tool to download
`Domains`-syntax [DNS blocklists](https://github.com/hagezi/dns-blocklists)
and export them in a format usable as input to an [unbound](https://www.nlnetlabs.nl/projects/unbound/about/)
DNS server.

## Features

* Download blocklists on a regular schedule and export them to `unbound`.
* Fully instrumented with [OpenTelemetry](https://opentelemetry.io/) for
  service monitoring.
* Platform independence. No platform-dependent code is included in any form,
  and installations can largely be carried between platforms without changes.
* [OCI](https://opencontainers.org/)-ready: Ready to run as an immutable,
  stateless, read-only, unprivileged container for maximum security and
  reliability.
* ISC license.

## Usage

To run the service, execute:

```
$ java -jar com.io7m.blockhead.jar run \
  --output-file-temporary list.txt.tmp \
  --output-file list.txt \
  --source https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/domains/ultimate.txt
```

By default, the service will download the blocklist from the given URL every
24 hours, process and write the results to `list.txt.tmp`, and then
atomically replace `list.txt` with `list.txt.tmp`. The practice of atomically
renaming ensures that, if `list.txt` exists, it can always be trusted to be
the most recently downloaded blocklist - there is no risk of observing a
half-written file.

For service monitoring, [OpenTelemetry](https://opentelemetry.io/) can be
enabled. The application produces traces, and will produce a `blockhead_up`
metric set to `1` whenever the service is up.

```
$ java -jar com.io7m.blockhead.jar run \
  --output-file-temporary list.txt.tmp \
  --output-file list.txt \
  --telemetry-service-name blockhead01 \
  --telemetry-metrics-address http://metrics.telemetry.example.com:4317 \
  --telemetry-metrics-protocol GRPC \
  --telemetry-logs-address http://logs.telemetry.example.com:4317 \
  --telemetry-logs-protocol GRPC \
  --telemetry-traces-address http://traces.telemetry.example.com:4317 \
  --telemetry-traces-protocol GRPC \
  --source https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/domains/ultimate.txt
```

## OCI

Container images are available at [Quay.io](https://quay.io/repository/io7mcom/blockhead).

The container image exposes an interface identical to the command-line interface
above, so simply replace "java -jar com.io7m.blockhead.jar" with a `podman`
invocation.

