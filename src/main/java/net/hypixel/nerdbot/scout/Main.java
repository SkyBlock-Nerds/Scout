package net.hypixel.nerdbot.scout;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.marmalade.json.DataSerialization;
import net.hypixel.nerdbot.scout.config.WatcherAppConfig;
import net.hypixel.nerdbot.scout.config.WatcherConfig;
import net.hypixel.nerdbot.scout.handler.update.SkyBlockUpdateDataHandler;
import net.hypixel.nerdbot.scout.watcher.HypixelThreadURLWatcher;
import net.hypixel.nerdbot.scout.watcher.URLWatcher;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

@Slf4j
public class Main {

    private static final List<AutoCloseable> activeWatchers = new ArrayList<>();
    private static final List<WatcherConfig> activeWatcherConfigs = new ArrayList<>();

    @Getter
    private static WatcherAppConfig config;

    public static void main(String[] args) {
        String configPath = "config.json";

        for (int i = 0; i < args.length; i++) {
            if ("--config".equals(args[i]) && i + 1 < args.length) {
                configPath = args[i + 1];
            }
        }

        log.info("NerdBot URL Watchers starting...");

        config = loadConfig(configPath);
        if (config == null) {
            log.error("Failed to load config from {}", configPath);
            System.exit(1);
            return;
        }

        log.info("Loaded config: {} watchers configured", config.getWatchers() != null ? config.getWatchers().size() : 0);

        ScoutMetrics.startMetricsServer();

        startWatchers();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down watchers...");
            stopWatchers();
            ScoutMetrics.stopMetricsServer();
        }));

        log.info("URL Watchers running.");

        // Keep the main thread alive
        CountDownLatch latch = new CountDownLatch(1);
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Finds the webhook URL configured for the watcher that uses the given handler class.
     */
    public static @Nullable String getWebhookUrlForHandler(String handlerClassName) {
        return activeWatcherConfigs.stream()
            .filter(wc -> handlerClassName.equals(wc.getHandlerClass()))
            .map(WatcherConfig::getWebhookUrl)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    /**
     * Finds the alert role ID configured for the watcher that uses the given handler class.
     */
    public static @Nullable String getAlertRoleIdForHandler(String handlerClassName) {
        return activeWatcherConfigs.stream()
            .filter(wc -> handlerClassName.equals(wc.getHandlerClass()))
            .map(WatcherConfig::getAlertRoleId)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    private static @Nullable WatcherAppConfig loadConfig(String path) {
        try (Reader reader = Files.newBufferedReader(Path.of(path), StandardCharsets.UTF_8)) {
            return DataSerialization.GSON.fromJson(reader, WatcherAppConfig.class);
        } catch (IOException e) {
            log.error("Failed to read config file: {}", path, e);
            return null;
        }
    }

    private static void startWatchers() {
        if (config.getWatchers() == null || config.getWatchers().isEmpty()) {
            log.info("No watchers configured");
            return;
        }

        config.getWatchers().stream()
            .filter(WatcherConfig::isEnabled)
            .forEach(Main::startWatcherFromConfig);
    }

    private static void startWatcherFromConfig(WatcherConfig watcherConfig) {
        try {
            if (!isAllowed(watcherConfig.getClassName())) {
                log.warn("Watcher class {} not permitted by class allowlist", watcherConfig.getClassName());
                return;
            }

            Class<?> watcherClazz = Class.forName(watcherConfig.getClassName());
            if (!URLWatcher.class.isAssignableFrom(watcherClazz)) {
                log.warn("Watcher class {} does not extend URLWatcher", watcherConfig.getClassName());
                return;
            }

            URLWatcher watcher;
            try {
                Constructor<?> declaredConstructor = watcherClazz.getDeclaredConstructor(String.class, Map.class);
                watcher = (URLWatcher) declaredConstructor.newInstance(watcherConfig.getUrl(), watcherConfig.getHeaders());
            } catch (NoSuchMethodException exception) {
                Constructor<?> declaredConstructor = watcherClazz.getDeclaredConstructor(String.class);
                watcher = (URLWatcher) declaredConstructor.newInstance(watcherConfig.getUrl());
            }

            if (watcherConfig.getHandlerClass() != null && !watcherConfig.getHandlerClass().isBlank()) {
                if (!isAllowed(watcherConfig.getHandlerClass())) {
                    log.warn("Handler class {} not permitted by class allowlist", watcherConfig.getHandlerClass());
                    return;
                }

                Class<?> handlerClazz = Class.forName(watcherConfig.getHandlerClass());
                if (!URLWatcher.DataHandler.class.isAssignableFrom(handlerClazz)) {
                    log.warn("Handler class {} does not implement URLWatcher.DataHandler", watcherConfig.getHandlerClass());
                    return;
                }

                URLWatcher.DataHandler handler = (URLWatcher.DataHandler) handlerClazz.getDeclaredConstructor().newInstance();
                log.info("Starting watcher {} on {} with handler {} (interval={} {})",
                    watcherClazz.getName(), watcherConfig.getUrl(), handlerClazz.getName(), watcherConfig.getInterval(), watcherConfig.getTimeUnit());

                activeWatcherConfigs.add(watcherConfig);
                watcher.startWatching(watcherConfig.getInterval(), watcherConfig.getTimeUnit(), handler);
                activeWatchers.add(watcher);
            } else if (watcher instanceof HypixelThreadURLWatcher hypixelWatcher) {
                String webhookUrl = watcherConfig.getWebhookUrl();
                String alertRoleId = watcherConfig.getAlertRoleId();

                hypixelWatcher.setThreadHandler(thread ->
                    SkyBlockUpdateDataHandler.handleThread(thread, webhookUrl, alertRoleId)
                );

                log.info("Starting HypixelThreadURLWatcher on {} (interval={} {})", watcherConfig.getUrl(), watcherConfig.getInterval(), watcherConfig.getTimeUnit());

                activeWatcherConfigs.add(watcherConfig);
                hypixelWatcher.startWatching(watcherConfig.getInterval(), watcherConfig.getTimeUnit());
                activeWatchers.add(hypixelWatcher);
            } else {
                log.warn("Watcher {} requires a handlerClass but none was provided", watcherConfig.getClassName());
            }
        } catch (Exception exception) {
            log.warn("Failed to start watcher from config: {}", watcherConfig, exception);
        }
    }

    private static boolean isAllowed(String className) {
        return className != null && className.startsWith("net.hypixel.nerdbot.");
    }

    private static void stopWatchers() {
        activeWatchers.forEach(watcher -> {
            try {
                watcher.close();
            } catch (Exception exception) {
                log.warn("Failed to stop watcher {}", watcher.getClass().getSimpleName(), exception);
            }
        });
        activeWatchers.clear();
        activeWatcherConfigs.clear();
    }
}
