package net.hypixel.nerdbot.scout;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.exporter.HTTPServer;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.marmalade.metrics.MetricsRegistry;

@Slf4j
public final class ScoutMetrics {

    private static final int DEFAULT_PORT = 9192;

    public static final Counter URL_FETCHES = MetricsRegistry.counter(
        "scout_url_fetches_total", "Total URL fetch attempts", "url", "status");

    public static final Histogram URL_FETCH_DURATION = MetricsRegistry.histogram(
        "scout_url_fetch_duration_seconds", "URL fetch duration in seconds",
        new double[]{0.1, 0.5, 1, 2, 5, 10}, "url");

    public static final Counter WEBHOOKS_SENT = MetricsRegistry.counter(
        "scout_webhooks_sent_total", "Total webhook messages sent", "status");

    public static final Counter DATA_CHANGES_DETECTED = MetricsRegistry.counter(
        "scout_data_changes_total", "Total data changes detected by handlers", "handler");

    public static final Gauge WATCHERS_ACTIVE = MetricsRegistry.gauge(
        "scout_watchers_active", "Number of active watchers");

    private static HTTPServer server;

    private ScoutMetrics() {
    }

    public static void startMetricsServer() {
        int port = Integer.getInteger("metrics.port", DEFAULT_PORT);

        try {
            if (server != null) {
                server.close();
            }

            server = MetricsRegistry.startServer(port);
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
