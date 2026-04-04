package net.hypixel.nerdbot.scout.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Getter
@Setter
@ToString
public class WatcherConfig {

    private boolean enabled = true;
    private @Nullable String className;
    private @Nullable String url;
    private @Nullable Map<String, String> headers;
    private long interval = 1;
    private TimeUnit timeUnit = TimeUnit.MINUTES;
    private @Nullable String handlerClass;
    private @Nullable String webhookUrl;

    /**
     * Optional role ID to mention when sending alerts.
     * Format in webhook content as {@code <@&roleId>}.
     */
    private @Nullable String alertRoleId;
}
