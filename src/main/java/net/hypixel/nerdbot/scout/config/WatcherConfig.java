package net.hypixel.nerdbot.scout.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Getter
@Setter
@ToString
public class WatcherConfig {

    private boolean enabled = true;
    private String className;
    private String url;
    private Map<String, String> headers;
    private long interval = 1;
    private TimeUnit timeUnit = TimeUnit.MINUTES;
    private String handlerClass;
    private String webhookUrl;

    /**
     * Optional role ID to mention when sending alerts.
     * Format in webhook content as {@code <@&roleId>}.
     */
    private String alertRoleId;
}
