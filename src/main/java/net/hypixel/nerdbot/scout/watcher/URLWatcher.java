package net.hypixel.nerdbot.scout.watcher;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.marmalade.format.StringUtils;
import net.hypixel.nerdbot.marmalade.Tuple;
import net.hypixel.nerdbot.scout.ScoutMetrics;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public abstract class URLWatcher implements AutoCloseable {

    @Getter
    private final String url;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledTask;
    private final OkHttpClient client;
    private final Map<String, String> headers;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    @Getter
    @Setter
    private String lastContent;
    @Getter
    private boolean active;

    public URLWatcher(String url) {
        this(url, null, true);
    }

    public URLWatcher(String url, Map<String, String> headers) {
        this(url, headers, true);
    }

    protected URLWatcher(String url, Map<String, String> headers, boolean loadInitialContent) {
        this.url = url;
        this.headers = headers;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "URLWatcher-" + sanitizeForThreadName(url));
            thread.setDaemon(true);
            return thread;
        });

        if (loadInitialContent) {
            this.lastContent = fetchContent();
        }
    }

    public void startWatching(long interval, TimeUnit unit, DataHandler handler) {
        if (closed.get()) {
            throw new IllegalStateException("Watcher for " + url + " has been closed");
        }

        if (scheduledTask != null && !scheduledTask.isDone()) {
            throw new IllegalStateException("Watcher for " + url + " is already active");
        }

        scheduledTask = scheduler.scheduleAtFixedRate(() -> fetchContentAsync()
            .thenAccept(newContent -> {
                if (newContent != null && !newContent.equals(lastContent)) {
                    String oldCompact = StringUtils.toOneLine(lastContent);
                    String newCompact = StringUtils.toOneLine(newContent);
                    log.debug("Watched {} and found changes! Old content: {} | New content: {}", url, oldCompact, newCompact);
                    handler.handleData(
                        lastContent,
                        newContent,
                        lastContent == null ? Collections.emptyList() : computeChangedValues(lastContent, newContent)
                    );
                    lastContent = newContent;
                }
            })
            .exceptionally(throwable -> {
                log.error("Error fetching content asynchronously from {}", url, throwable);
                return null;
            }), 0, unit.toMillis(interval), TimeUnit.MILLISECONDS);

        log.info("Started watching {}", url);
        active = true;
        ScoutMetrics.WATCHERS_ACTIVE.inc();
    }

    public void watchOnce(DataHandler handler) {
        active = true;

        fetchContentAsync()
            .thenAccept(newContent -> {
                if (newContent != null && !newContent.equals(lastContent)) {
                    handler.handleData(
                        lastContent,
                        newContent,
                        lastContent == null ? Collections.emptyList() : computeChangedValues(lastContent, newContent)
                    );
                    lastContent = newContent;
                    String oldCompact = StringUtils.toOneLine(lastContent);
                    String newCompact = StringUtils.toOneLine(newContent);
                    log.debug("Watched {} once, found changes! Old content: {} | New content: {}", url, oldCompact, newCompact);
                }
                active = false;
            })
            .exceptionally(throwable -> {
                log.error("Error fetching content asynchronously from " + url, throwable);
                active = false;
                return null;
            });
    }

    public void stopWatching() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        if (scheduledTask != null) {
            scheduledTask.cancel(true);
            scheduledTask = null;
        }

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Scheduler for {} did not terminate gracefully, forcing shutdown", url);
                scheduler.shutdownNow();
            }
        } catch (InterruptedException exception) {
            log.warn("Interrupted while waiting for scheduler termination for {}", url);
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        ScoutMetrics.WATCHERS_ACTIVE.dec();
        active = false;
        log.info("Stopped watching {}", url);
    }

    public String fetchContent() {
        log.debug("Fetching content from " + url);

        Request.Builder requestBuilder = new Request.Builder().url(url);

        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                requestBuilder.header(entry.getKey(), entry.getValue());
            }
        }

        Request request = requestBuilder.build();
        long startNanos = System.nanoTime();

        try (Response response = client.newCall(request).execute()) {
            double durationSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
            ScoutMetrics.URL_FETCH_DURATION.labels(url).observe(durationSeconds);

            if (response.isSuccessful()) {
                String content = response.body().string();
                ScoutMetrics.URL_FETCHES.labels(url, "success").inc();
                log.debug("Successfully fetched content from {}! (Content: {})", url, StringUtils.toOneLine(content));
                return content;
            } else {
                ScoutMetrics.URL_FETCHES.labels(url, "failure").inc();
                log.error("Failed to fetch content from " + url + "! (Response: " + response + ")");
            }
        } catch (IOException exception) {
            double durationSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
            ScoutMetrics.URL_FETCH_DURATION.labels(url).observe(durationSeconds);
            ScoutMetrics.URL_FETCHES.labels(url, "error").inc();
            log.error("Failed to fetch content from " + url + "!", exception);
        }

        return null;
    }

    public CompletableFuture<String> fetchContentAsync() {
        log.debug("Fetching content asynchronously from " + url);

        Request.Builder requestBuilder = new Request.Builder().url(url);

        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                requestBuilder.header(entry.getKey(), entry.getValue());
            }
        }

        Request request = requestBuilder.build();
        CompletableFuture<String> future = new CompletableFuture<>();
        long startNanos = System.nanoTime();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                double durationSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
                ScoutMetrics.URL_FETCH_DURATION.labels(url).observe(durationSeconds);
                ScoutMetrics.URL_FETCHES.labels(url, "error").inc();
                log.error("Failed to fetch content asynchronously from " + url, e);
                future.complete(null);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                double durationSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
                ScoutMetrics.URL_FETCH_DURATION.labels(url).observe(durationSeconds);

                try (response) {
                    if (response.isSuccessful()) {
                        ScoutMetrics.URL_FETCHES.labels(url, "success").inc();
                        String content = response.body().string();
                        log.debug("Successfully fetched content asynchronously from {}! (Content: {})", url, StringUtils.toOneLine(content));
                        future.complete(content);
                    } else {
                        ScoutMetrics.URL_FETCHES.labels(url, "failure").inc();
                        log.error("Failed to fetch content asynchronously from " + url + "! (Response: " + response + ")");
                        future.complete(null);
                    }
                } catch (IOException e) {
                    ScoutMetrics.URL_FETCHES.labels(url, "error").inc();
                    log.error("Error reading response body from " + url, e);
                    future.complete(null);
                }
            }
        });

        return future;
    }

    public interface DataHandler {
        void handleData(String oldContent, String newContent, List<Tuple<String, Object, Object>> changedValues);
    }

    @Override
    public void close() {
        stopWatching();
    }

    private static String sanitizeForThreadName(String url) {
        return url.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    protected abstract List<Tuple<String, Object, Object>> computeChangedValues(String oldContent, String newContent);
}
