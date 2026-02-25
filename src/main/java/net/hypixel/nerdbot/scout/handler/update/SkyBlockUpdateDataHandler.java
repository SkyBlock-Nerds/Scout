package net.hypixel.nerdbot.scout.handler.update;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.scout.ScoutMetrics;
import net.hypixel.nerdbot.scout.webhook.DiscordWebhook;
import net.hypixel.nerdbot.scout.xml.SkyBlockThreadParser.HypixelThread;
import org.jetbrains.annotations.Nullable;

@Slf4j
public final class SkyBlockUpdateDataHandler {

    private SkyBlockUpdateDataHandler() {
    }

    public static void handleThread(HypixelThread hypixelThread, @Nullable String webhookUrl, @Nullable String alertRoleId) {
        // Simple check to make sure only SkyBlock threads are sent
        if (!hypixelThread.getForum().equals("SkyBlock Patch Notes") && !hypixelThread.getTitle().contains("SkyBlock")) {
            return;
        }

        ScoutMetrics.DATA_CHANGES_DETECTED.labels("skyblock-updates").inc();

        if (webhookUrl != null) {
            StringBuilder content = new StringBuilder();

            if (alertRoleId != null && !alertRoleId.isBlank()) {
                content.append("<@&").append(alertRoleId).append(">\n\n");
            }

            content.append(hypixelThread.getLink());
            DiscordWebhook.sendMessage(webhookUrl, content.toString());
        } else {
            log.warn("No webhook URL configured for SkyBlock update handler");
        }
    }
}
