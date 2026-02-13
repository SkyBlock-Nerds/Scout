package net.hypixel.nerdbot.scout.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.hypixel.nerdbot.scout.handler.status.StatusPageConfig;

import java.util.List;

@Getter
@Setter
@ToString
public class WatcherAppConfig {

    private List<WatcherConfig> watchers;
    private StatusPageConfig statusPageConfig = new StatusPageConfig();
}
