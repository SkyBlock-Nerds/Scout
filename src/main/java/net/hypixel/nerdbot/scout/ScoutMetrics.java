package net.hypixel.nerdbot.scout;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ScoutMetrics {

    private static final int DEFAULT_PORT = 9192;

    public static final Counter URL_FETCHES = Counter.build()
        .name("scout_url_fetches_total")
        .help("Total URL fetch attempts")
        .labelNames("url", "status")
        .register();

    public static final Histogram URL_FETCH_DURATION = Histogram.build()
        .name("scout_url_fetch_duration_seconds")
        .help("URL fetch duration in seconds")
        .labelNames("url")
        .buckets(0.1, 0.5, 1, 2, 5, 10)
        .register();

    public static final Counter WEBHOOKS_SENT = Counter.build()
        .name("scout_webhooks_sent_total")
        .help("Total webhook messages sent")
        .labelNames("status")
        .register();

    public static final Counter DATA_CHANGES_DETECTED = Counter.build()
        .name("scout_data_changes_total")
        .help("Total data changes detected by handlers")
        .labelNames("handler")
        .register();

    public static final Gauge WATCHERS_ACTIVE = Gauge.build()
        .name("scout_watchers_active")
        .help("Number of active watchers")
        .register();

    private static HTTPServer server;

    private ScoutMetrics() {
    }

    public static void startMetricsServer() {
        int port = Integer.getInteger("metrics.port", DEFAULT_PORT);

        try {
            if (server != null) {
                server.close();
            }

            server = new HTTPServer.Builder()
                .withPort(port)
                .build();

            DefaultExports.initialize();
            log.info("Prometheus metrics server started on port {}", port);
        } catch (Exception e) {
            log.error("Failed to start Prometheus metrics server on port {}", port, e);
        }
    }

    public static void stopMetricsServer() {
        if (server != null) {
            server.close();
            server = null;
            log.info("Prometheus metrics server stopped");
        }
    }
}