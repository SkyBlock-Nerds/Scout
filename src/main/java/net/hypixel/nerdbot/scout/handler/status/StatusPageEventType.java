package net.hypixel.nerdbot.scout.handler.status;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StatusPageEventType {
    NEW_INCIDENT("\uD83D\uDED1", "New Incident"),
    INCIDENT_UPDATE("\uD83D\uDD04", "Incident Update"),
    INCIDENT_RESOLVED("\u2705", "Incident Resolved"),
    NEW_MAINTENANCE("\uD83D\uDD27", "Scheduled Maintenance"),
    MAINTENANCE_UPDATE("\uD83D\uDD04", "Maintenance Update"),
    MAINTENANCE_COMPLETED("\u2705", "Maintenance Completed"),
    COMPONENT_STATUS_CHANGE("\u26A0\uFE0F", "Component Status Change");

    private final String emoji;
    private final String displayName;

    public static StatusPageEventType fromIncidentStatus(String status, boolean isNew) {
        if (isNew) {
            return status.equalsIgnoreCase("resolved") ? INCIDENT_RESOLVED : NEW_INCIDENT;
        }

        return status.equalsIgnoreCase("resolved") ? INCIDENT_RESOLVED : INCIDENT_UPDATE;
    }

    public static StatusPageEventType fromMaintenanceStatus(String status, boolean isNew) {
        if (isNew) {
            return status.equalsIgnoreCase("completed") ? MAINTENANCE_COMPLETED : NEW_MAINTENANCE;
        }

        return status.equalsIgnoreCase("completed") ? MAINTENANCE_COMPLETED : MAINTENANCE_UPDATE;
    }
}
