package net.hypixel.nerdbot.scout.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.hypixel.nerdbot.scout.handler.status.StatusPageConfig;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Getter
@Setter
@ToString
public class WatcherAppConfig {

    private @Nullable List<WatcherConfig> watchers;
    private StatusPageConfig statusPageConfig = new StatusPageConfig();
}
