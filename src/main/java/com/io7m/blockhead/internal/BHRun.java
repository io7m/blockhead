/*
 * Copyright © 2024 Mark Raynsford <code@io7m.com> https://www.io7m.com
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR
 * IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */


package com.io7m.blockhead.internal;

import com.io7m.blockhead.BHVersion;
import com.io7m.quarrel.core.QCommandContextType;
import com.io7m.quarrel.core.QCommandMetadata;
import com.io7m.quarrel.core.QCommandStatus;
import com.io7m.quarrel.core.QCommandType;
import com.io7m.quarrel.core.QParameterNamed01;
import com.io7m.quarrel.core.QParameterNamed1;
import com.io7m.quarrel.core.QParameterNamedType;
import com.io7m.quarrel.core.QStringType;
import com.io7m.quarrel.ext.logback.QLogback;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * "run"
 */

public final class BHRun implements QCommandType
{
  private static final Logger LOG =
    LoggerFactory.getLogger(BHRun.class);

  private static final QParameterNamed1<URI> SOURCE_URL =
    new QParameterNamed1<>(
      "--source",
      List.of(),
      new QStringType.QConstant("The source URL of the blocklist."),
      Optional.empty(),
      URI.class
    );

  private static final QParameterNamed1<Duration> DOWNLOAD_FREQUENCY =
    new QParameterNamed1<>(
      "--frequency",
      List.of(),
      new QStringType.QConstant("The update frequency."),
      Optional.of(Duration.ofHours(24L)),
      Duration.class
    );

  private static final QParameterNamed1<Path> OUTPUT_FILE =
    new QParameterNamed1<>(
      "--output-file",
      List.of(),
      new QStringType.QConstant("The output file."),
      Optional.empty(),
      Path.class
    );

  private static final QParameterNamed1<Path> OUTPUT_FILE_TMP =
    new QParameterNamed1<>(
      "--output-file-temporary",
      List.of(),
      new QStringType.QConstant("The temporary output file."),
      Optional.empty(),
      Path.class
    );

  private static final QParameterNamed01<String> TELEMETRY_SERVICE_NAME =
    new QParameterNamed01<>(
      "--telemetry-service-name",
      List.of(),
      new QStringType.QConstant("The telemetry service name."),
      Optional.empty(),
      String.class
    );

  private static final QParameterNamed01<URI> TELEMETRY_METRICS_ADDRESS =
    new QParameterNamed01<>(
      "--telemetry-metrics-address",
      List.of(),
      new QStringType.QConstant("The telemetry metrics address."),
      Optional.empty(),
      URI.class
    );

  private static final QParameterNamed1<BHTelemetryConfiguration.OTLPProtocol> TELEMETRY_METRICS_PROTOCOL =
    new QParameterNamed1<>(
      "--telemetry-metrics-protocol",
      List.of(),
      new QStringType.QConstant("The telemetry metrics protocol."),
      Optional.of(BHTelemetryConfiguration.OTLPProtocol.GRPC),
      BHTelemetryConfiguration.OTLPProtocol.class
    );

  private static final QParameterNamed01<URI> TELEMETRY_LOGS_ADDRESS =
    new QParameterNamed01<>(
      "--telemetry-logs-address",
      List.of(),
      new QStringType.QConstant("The telemetry logs address."),
      Optional.empty(),
      URI.class
    );

  private static final QParameterNamed1<BHTelemetryConfiguration.OTLPProtocol> TELEMETRY_LOGS_PROTOCOL =
    new QParameterNamed1<>(
      "--telemetry-logs-protocol",
      List.of(),
      new QStringType.QConstant("The telemetry logs protocol."),
      Optional.of(BHTelemetryConfiguration.OTLPProtocol.GRPC),
      BHTelemetryConfiguration.OTLPProtocol.class
    );

  private static final QParameterNamed01<URI> TELEMETRY_TRACES_ADDRESS =
    new QParameterNamed01<>(
      "--telemetry-traces-address",
      List.of(),
      new QStringType.QConstant("The telemetry traces address."),
      Optional.empty(),
      URI.class
    );

  private static final QParameterNamed1<BHTelemetryConfiguration.OTLPProtocol> TELEMETRY_TRACES_PROTOCOL =
    new QParameterNamed1<>(
      "--telemetry-traces-protocol",
      List.of(),
      new QStringType.QConstant("The telemetry traces protocol."),
      Optional.of(BHTelemetryConfiguration.OTLPProtocol.GRPC),
      BHTelemetryConfiguration.OTLPProtocol.class
    );

  private final QCommandMetadata metadata;
  private BHTelemetryServiceType telemetry;
  private Duration waitDuration;
  private URI source;
  private HttpClient httpClient;
  private Path outputFile;
  private Path outputFileTmp;

  /**
   * "run"
   */

  public BHRun()
  {
    this.metadata = new QCommandMetadata(
      "run",
      new QStringType.QConstant("Run the blockhead tool."),
      Optional.empty()
    );
  }

  @Override
  public List<QParameterNamedType<?>> onListNamedParameters()
  {
    return Stream.concat(
      Stream.of(
        DOWNLOAD_FREQUENCY,
        OUTPUT_FILE,
        OUTPUT_FILE_TMP,
        SOURCE_URL,
        TELEMETRY_LOGS_ADDRESS,
        TELEMETRY_LOGS_PROTOCOL,
        TELEMETRY_METRICS_ADDRESS,
        TELEMETRY_METRICS_PROTOCOL,
        TELEMETRY_SERVICE_NAME,
        TELEMETRY_TRACES_ADDRESS,
        TELEMETRY_TRACES_PROTOCOL
      ),
      QLogback.parameters().stream()
    ).toList();
  }

  @Override
  public QCommandStatus onExecute(
    final QCommandContextType context)
  {
    QLogback.configure(context);

    this.source =
      context.parameterValue(SOURCE_URL);
    this.waitDuration =
      context.parameterValue(DOWNLOAD_FREQUENCY);
    this.outputFile =
      context.parameterValue(OUTPUT_FILE);
    this.outputFileTmp =
      context.parameterValue(OUTPUT_FILE_TMP);

    this.httpClient =
      HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .executor(Executors.newVirtualThreadPerTaskExecutor())
        .build();

    this.initializeTelemetry(context);

    while (true) {
      this.fetchBlockList();
      this.waitForDuration();
    }
  }

  private void waitForDuration()
  {
    LOG.debug(
      "Waiting {} until next download attempt.",
      this.waitDuration
    );

    try {
      Thread.sleep(this.waitDuration.toMillis());
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void fetchBlockList()
  {
    final var span =
      this.telemetry.tracer()
        .spanBuilder("FetchBlockList")
        .startSpan();

    try (final var ignored = span.makeCurrent()) {
      final var maxAttempts = 10;
      for (int attempt = 1; attempt <= maxAttempts; ++attempt) {
        LOG.debug(
          "Downloading blocklist from {} (attempt {} of {})",
          this.source,
          attempt,
          maxAttempts
        );

        try {
          span.setAttribute("SourceURL", this.source.toString());
          span.setAttribute("Attempt", attempt);

          try (final var lines = this.downloadList()) {
            this.processFile(lines);
          }
          span.setStatus(StatusCode.OK);
          return;
        } catch (final Throwable e) {
          BHTelemetryServiceType.recordSpanException(e);
          try {
            Thread.sleep(1_000L);
          } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
          }
        }
      }
      span.setStatus(StatusCode.ERROR);
    } finally {
      span.end();
    }
  }

  private void processFile(
    final Stream<String> lines)
    throws IOException
  {
    LOG.debug("Processing blocklist");

    final var span =
      this.telemetry.tracer()
        .spanBuilder("ProcessBlockList")
        .startSpan();

    long processed = 0L;

    try (final var ignored = span.makeCurrent()) {
      try (final var output = Files.newBufferedWriter(
        this.outputFileTmp,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING)) {

        output.write("local-zone: \"0.0.0.0\" redirect\n");
        output.write("local-data: \"0.0.0.0 A 0.0.0.0\"\n");

        final var iterator = lines.iterator();
        while (iterator.hasNext()) {
          final var line = iterator.next().trim();
          if (line.startsWith("#")) {
            continue;
          }

          output.write("local-zone: \"%s\" redirect\n".formatted(line));
          output.write("local-data: \"%s A 0.0.0.0\"\n".formatted(line));
          ++processed;
        }

        output.flush();
        span.setAttribute("ProcessedEntries", processed);
      }

      LOG.debug(
        "Processed {} blocklist entries",
        Long.toUnsignedString(processed)
      );

      Files.move(
        this.outputFileTmp,
        this.outputFile,
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE
      );
    } finally {
      span.end();
    }
  }

  private Stream<String> downloadList()
    throws IOException, InterruptedException
  {
    final var span =
      this.telemetry.tracer()
        .spanBuilder("DownloadList")
        .setParent(Context.current())
        .startSpan();

    try (final var ignored = span.makeCurrent()) {
      final var response =
        this.httpClient.send(
          HttpRequest.newBuilder(this.source)
            .header("User-Agent", userAgent())
            .build(),
          HttpResponse.BodyHandlers.ofLines()
        );

      if (response.statusCode() >= 400) {
        span.setAttribute("HTTPStatus", response.statusCode());
        span.setStatus(StatusCode.ERROR);

        throw new IOException(
          "Server returned an error: %d".formatted(response.statusCode())
        );
      }

      span.setStatus(StatusCode.OK);
      return response.body();
    } finally {
      span.end();
    }
  }

  private static String userAgent()
  {
    return "com.io7m.blockhead %s".formatted(BHVersion.MAIN_VERSION);
  }

  private void initializeTelemetry(
    final QCommandContextType context)
  {
    final var serviceName =
      context.parameterValue(TELEMETRY_SERVICE_NAME)
        .orElse("blockhead");

    final var logs =
      context.parameterValue(TELEMETRY_LOGS_ADDRESS)
        .map(address -> {
          return new BHTelemetryConfiguration.Logs(
            address,
            context.parameterValue(TELEMETRY_LOGS_PROTOCOL)
          );
        });

    final var metrics =
      context.parameterValue(TELEMETRY_METRICS_ADDRESS)
        .map(address -> {
          return new BHTelemetryConfiguration.Metrics(
            address,
            context.parameterValue(TELEMETRY_METRICS_PROTOCOL)
          );
        });

    final var traces =
      context.parameterValue(TELEMETRY_TRACES_ADDRESS)
        .map(address -> {
          return new BHTelemetryConfiguration.Traces(
            address,
            context.parameterValue(TELEMETRY_TRACES_PROTOCOL)
          );
        });

    final var telemetryServices =
      new BHTelemetryServices();

    this.telemetry =
      telemetryServices.create(
        new BHTelemetryConfiguration(
          serviceName,
          logs,
          metrics,
          traces
        )
      );

    final var meterUp =
      this.telemetry.meter()
        .gaugeBuilder("blockhead_up")
        .ofLongs()
        .build();

    meterUp.set(1L);
  }

  @Override
  public QCommandMetadata metadata()
  {
    return this.metadata;
  }
}
