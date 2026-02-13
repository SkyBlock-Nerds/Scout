package net.hypixel.nerdbot.scout.handler.status;

import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.marmalade.Tuple;
import net.hypixel.nerdbot.marmalade.json.DataSerialization;
import net.hypixel.nerdbot.scout.Main;
import net.hypixel.nerdbot.scout.ScoutMetrics;
import net.hypixel.nerdbot.scout.webhook.DiscordWebhook;
import net.hypixel.nerdbot.scout.watcher.URLWatcher;

import java.util.List;
import java.util.stream.Stream;

@Slf4j
public class StatusPageDataHandler implements URLWatcher.DataHandler {

    private final StatusPageConfig config;
    private final StatusPageChangeDetector changeDetector;
    private final StatusPageEmbedFactory embedFactory;
    private final String webhookUrl;
    private final String alertRoleId;

    public StatusPageDataHandler() {
        this.config = Main.getConfig().getStatusPageConfig();
        this.changeDetector = new StatusPageChangeDetector(config);
        this.embedFactory = new StatusPageEmbedFactory(config);
        this.webhookUrl = null;
        this.alertRoleId = null;
    }

    public StatusPageDataHandler(StatusPageConfig config, String webhookUrl, String alertRoleId) {
        this.config = config;
        this.changeDetector = new StatusPageChangeDetector(config);
        this.embedFactory = new StatusPageEmbedFactory(config);
        this.webhookUrl = webhookUrl;
        this.alertRoleId = alertRoleId;
    }

    @Override
    public void handleData(String oldContent, String newContent, List<Tuple<String, Object, Object>> changedValues) {
        log.info("Status page data changed!");
        ScoutMetrics.DATA_CHANGES_DETECTED.labels("status-page").inc();

        try {
            StatusPageResponse oldData = parseStatusData(oldContent);
            StatusPageResponse newData = parseStatusData(newContent);

            if (newData == null) {
                log.warn("Failed to parse new status page data");
                return;
            }

            List<JsonObject> embedsToSend = Stream.of(
                processIncidents(oldData, newData),
                processMaintenances(oldData, newData)
            ).flatMap(List::stream).toList();

            boolean shouldPing = hasIncidentChanges(oldData, newData) && config.isEnableStatusAlerts() ||
                hasMaintenanceChanges(oldData, newData) && config.isEnableMaintenanceAlerts();

            if (!embedsToSend.isEmpty()) {
                String content = null;
                if (shouldPing && alertRoleId != null && !alertRoleId.isBlank()) {
                    content = "<@&" + alertRoleId + ">";
                }

                if (webhookUrl != null) {
                    DiscordWebhook.send(webhookUrl, content, embedsToSend);
                } else {
                    log.warn("No webhook URL configured for status page handler");
                }

                log.info("Sent {} status embeds via webhook", embedsToSend.size());
            } else {
                log.debug("No significant status changes detected");
            }
        } catch (Exception e) {
            log.error("Error processing status page data", e);
        }
    }

    private StatusPageResponse parseStatusData(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }

        try {
            return DataSerialization.GSON.fromJson(content, StatusPageResponse.class);
        } catch (Exception e) {
            log.error("Failed to parse status page data", e);
            return null;
        }
    }

    private List<JsonObject> processIncidents(StatusPageResponse oldData, StatusPageResponse newData) {
        return Stream.concat(
            processNewIncidents(oldData, newData),
            processUpdatedIncidents(oldData, newData)
        ).toList();
    }

    private List<JsonObject> processMaintenances(StatusPageResponse oldData, StatusPageResponse newData) {
        return Stream.concat(
            processNewMaintenances(oldData, newData),
            processUpdatedMaintenances(oldData, newData)
        ).toList();
    }

    private Stream<JsonObject> processNewIncidents(StatusPageResponse oldData, StatusPageResponse newData) {
        return changeDetector.findNewIncidents(oldData, newData).stream()
            .peek(incident -> log.info("Found new incident: {} ({})", incident.getName(), incident.getStatus()))
            .map(incident -> {
                StatusPageEventType eventType = StatusPageEventType.fromIncidentStatus(incident.getStatus(), true);
                return embedFactory.createIncidentEmbed(incident, eventType);
            });
    }

    private Stream<JsonObject> processUpdatedIncidents(StatusPageResponse oldData, StatusPageResponse newData) {
        return changeDetector.findUpdatedIncidents(oldData, newData).stream()
            .peek(incident -> log.info("Found updated incident: {} ({})", incident.getName(), incident.getStatus()))
            .map(incident -> {
                StatusPageEventType eventType = StatusPageEventType.fromIncidentStatus(incident.getStatus(), false);
                return embedFactory.createIncidentEmbed(incident, eventType);
            });
    }

    private Stream<JsonObject> processNewMaintenances(StatusPageResponse oldData, StatusPageResponse newData) {
        return changeDetector.findNewMaintenances(oldData, newData).stream()
            .peek(maintenance -> log.info("Found new maintenance: {} ({})", maintenance.getName(), maintenance.getStatus()))
            .map(maintenance -> {
                StatusPageEventType eventType = StatusPageEventType.fromMaintenanceStatus(maintenance.getStatus(), true);
                return embedFactory.createMaintenanceEmbed(maintenance, eventType);
            });
    }

    private Stream<JsonObject> processUpdatedMaintenances(StatusPageResponse oldData, StatusPageResponse newData) {
        return changeDetector.findUpdatedMaintenances(oldData, newData).stream()
            .peek(maintenance -> log.info("Found updated maintenance: {} ({})", maintenance.getName(), maintenance.getStatus()))
            .map(maintenance -> {
                StatusPageEventType eventType = StatusPageEventType.fromMaintenanceStatus(maintenance.getStatus(), false);
                return embedFactory.createMaintenanceEmbed(maintenance, eventType);
            });
    }

    private boolean hasIncidentChanges(StatusPageResponse oldData, StatusPageResponse newData) {
        return !changeDetector.findNewIncidents(oldData, newData).isEmpty() ||
            !changeDetector.findUpdatedIncidents(oldData, newData).isEmpty();
    }

    private boolean hasMaintenanceChanges(StatusPageResponse oldData, StatusPageResponse newData) {
        return !changeDetector.findNewMaintenances(oldData, newData).isEmpty() ||
            !changeDetector.findUpdatedMaintenances(oldData, newData).isEmpty();
    }
}
