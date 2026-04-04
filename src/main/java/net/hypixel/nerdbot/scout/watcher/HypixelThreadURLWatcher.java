package net.hypixel.nerdbot.scout.watcher;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.scout.xml.SkyBlockThreadParser;
import net.hypixel.nerdbot.scout.xml.SkyBlockThreadParser.HypixelThread;

import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Slf4j
public class HypixelThreadURLWatcher extends XmlURLWatcher {

    private final AtomicInteger lastGuid = new AtomicInteger();
    private Consumer<HypixelThread> threadHandler;
    private volatile boolean initialised;
    private final CompletableFuture<Void> baselineFuture;
    private final AtomicBoolean startScheduled = new AtomicBoolean(false);

    public HypixelThreadURLWatcher(String url) {
        this(url, null, 0, null);
    }

    public HypixelThreadURLWatcher(String url, @Nullable Map<String, String> headers) {
        this(url, headers, 0, null);
    }

    public HypixelThreadURLWatcher(String url, @Nullable Map<String, String> headers, int initialGuid) {
        this(url, headers, initialGuid, null);
    }

    public HypixelThreadURLWatcher(String url, @Nullable Map<String, String> headers, int initialGuid, @Nullable Consumer<HypixelThread> threadHandler) {
        super(url, headers, false);
        this.threadHandler = threadHandler;
        this.lastGuid.set(Math.max(0, initialGuid));
        this.initialised = initialGuid > 0;
        this.baselineFuture = this.initialised ? CompletableFuture.completedFuture(null) : seedBaseline();
    }

    /**
     * Sets the thread handler used by this watcher. This must be called before
     * {@link #startWatching(long, TimeUnit)} if no handler was provided at construction time.
     */
    public void setThreadHandler(Consumer<HypixelThread> handler) {
        if (startScheduled.get()) {
            throw new IllegalStateException("Cannot set thread handler after watcher has been started");
        }

        this.threadHandler = handler;
    }

    public void startWatching(long interval, TimeUnit unit) {
        if (!startScheduled.compareAndSet(false, true)) {
            throw new IllegalStateException("Watcher for " + getUrl() + " has already been started");
        }

        baselineFuture.whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                log.warn("Baseline initialisation for {} encountered an error", getUrl(), throwable);
            }

            try {
                super.startWatching(interval, unit, (oldContent, newContent, changedValues) -> handleContent(newContent));
            } catch (IllegalStateException exception) {
                log.debug("Skipping start for {}: {}", getUrl(), exception.getMessage());
            }
        });
    }

    public void watchOnce() {
        baselineFuture.whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                log.warn("Baseline initialisation for {} encountered an error", getUrl(), throwable);
            }
            super.watchOnce((oldContent, newContent, changedValues) -> handleContent(newContent));
        });
    }

    private void handleContent(String newContent) {
        List<HypixelThread> hypixelThreads = parseThreads(newContent);
        if (hypixelThreads == null || hypixelThreads.isEmpty()) {
            return;
        }

        if (!initialised) {
            lastGuid.set(
                hypixelThreads.stream()
                    .mapToInt(HypixelThread::getGuid)
                    .max()
                    .orElse(lastGuid.get())
            );
            initialised = true;
            return;
        }

        hypixelThreads.stream()
            .sorted(Comparator.comparingInt(HypixelThread::getGuid))
            .forEach(this::updateLastGuid);
    }

    private CompletableFuture<Void> seedBaseline() {
        return fetchContentAsync()
            .handle((content, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to seed baseline for {}", getUrl(), throwable);
                    return null;
                }
                return content;
            })
            .thenAccept(content -> {
                if (content != null) {
                    List<HypixelThread> hypixelThreads = parseThreads(content);
                    if (hypixelThreads != null && !hypixelThreads.isEmpty()) {
                        lastGuid.set(
                            hypixelThreads.stream()
                                .mapToInt(HypixelThread::getGuid)
                                .max()
                                .orElse(lastGuid.get())
                        );
                        initialised = true;
                        return;
                    }
                }
                initialised = false;
            });
    }

    private void updateLastGuid(HypixelThread thread) {
        int candidateGuid = thread.getGuid();
        while (true) {
            int previousGuid = lastGuid.get();
            if (candidateGuid <= previousGuid) {
                return;
            }

            if (lastGuid.compareAndSet(previousGuid, candidateGuid)) {
                log.debug("Watched {} and found newest GUID! Old: {} - New: {}", getUrl(), previousGuid, candidateGuid);
                if (threadHandler != null) {
                    threadHandler.accept(thread);
                } else {
                    log.warn("No thread handler configured for watcher on {}, skipping thread: {}", getUrl(), thread.getTitle());
                }
                return;
            }
        }
    }

    private @Nullable List<HypixelThread> parseThreads(@Nullable String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        return SkyBlockThreadParser.parseSkyBlockThreads(content);
    }
}
