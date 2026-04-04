package net.hypixel.nerdbot.scout.watcher;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.marmalade.concurrent.ExecutorFactory;
import net.hypixel.nerdbot.marmalade.exception.HttpException;
import net.hypixel.nerdbot.marmalade.format.StringUtils;
import net.hypixel.nerdbot.marmalade.functional.Result;
import net.hypixel.nerdbot.marmalade.resilience.Retry;
import net.hypixel.nerdbot.marmalade.Tuple;
import net.hypixel.nerdbot.scout.ScoutMetrics;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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

    public URLWatcher(String url, @Nullable Map<String, String> headers) {
        this(url, headers, true);
    }

    protected URLWatcher(String url, @Nullable Map<String, String> headers, boolean loadInitialContent) {
        this.url = url;
        this.headers = headers;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
        this.scheduler = ExecutorFactory.newScheduledDaemon("URLWatcher-" + sanitizeForThreadName(url));

        if (loadInitialContent) {
            this.lastContent = fetchContent().orElse(null);
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
            .thenAccept(result -> {
                if (result.isFailure()) {
                    log.error("Failed to fetch content from {}: {}", url, result);
                    return;
                }
                String newContent = result.orElse(null);
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
            .thenAccept(result -> {
                if (result.isFailure()) {
                    log.error("Failed to fetch content from {}: {}", url, result);
                    active = false;
                    return;
                }
                String newContent = result.orElse(null);
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
                log.error("Error fetching content asynchronously from {}", url, throwable);
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

    /**
     * Performs a single HTTP GET to {@link #url}, returning the response body on success or throwing
     * {@link HttpException} on a non-successful status or network error. Intended to be called only
     * from within {@link #fetchContent()}'s Retry wrapper.
     */
    private String fetchOnce() throws HttpException {
        log.debug("Fetching content from {}", url);

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
                log.error("Failed to fetch content from {}! (Response: {})", url, response);
                throw new HttpException("HTTP " + response.code() + " for " + url, response.code(), url);
            }
        } catch (HttpException e) {
            throw e;
        } catch (IOException e) {
            double durationSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
            ScoutMetrics.URL_FETCH_DURATION.labels(url).observe(durationSeconds);
            ScoutMetrics.URL_FETCHES.labels(url, "error").inc();
            log.error("Failed to fetch content from {}!", url, e);
            throw new HttpException("Network error fetching " + url, e);
        }
    }

    public Result<String, HttpException> fetchContent() {
        try {
            String content = Retry.of(this::fetchOnce)
                .maxAttempts(3)
                .delay(Duration.ofMillis(500))
                .backoffMultiplier(2.0)
                .retryOn(HttpException.class)
                .execute();
            return Result.success(content);
        } catch (Retry.RetryExhaustedException e) {
            Throwable cause = e.getCause();
            if (cause instanceof HttpException httpException) {
                return Result.failure(httpException);
            }
            return Result.failure(new HttpException("Failed after retries: " + url, e));
        }
    }

    public CompletableFuture<Result<String, HttpException>> fetchContentAsync() {
        log.debug("Fetching content asynchronously from {}", url);
        return Retry.of(this::fetchOnce)
            .maxAttempts(3)
            .delay(Duration.ofMillis(500))
            .backoffMultiplier(2.0)
            .retryOn(HttpException.class)
            .executeAsync()
            .handle((content, throwable) -> {
                if (throwable instanceof Retry.RetryExhaustedException retryEx) {
                    Throwable cause = retryEx.getCause();
                    if (cause instanceof HttpException httpException) {
                        return Result.failure(httpException);
                    }
                    return Result.<String, HttpException>failure(new HttpException("Failed after retries: " + url, retryEx));
                } else if (throwable != null) {
                    return Result.<String, HttpException>failure(new HttpException("Network error fetching " + url, throwable));
                }
                return Result.<String, HttpException>success(content);
            });
    }

    public interface DataHandler {
        void handleData(@Nullable String oldContent, String newContent, List<Tuple<String, Object, Object>> changedValues);
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
