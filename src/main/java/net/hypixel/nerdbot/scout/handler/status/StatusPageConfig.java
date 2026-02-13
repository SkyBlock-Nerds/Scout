package net.hypixel.nerdbot.scout.handler.status;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.awt.Color;

@Getter
@Setter
@ToString
public class StatusPageConfig {

    private String operationalColor = "00C851";
    private String degradedColor = "FFBB33";
    private String partialOutageColor = "FF4444";
    private String majorOutageColor = "8B0000";
    private String maintenanceColor = "3498DB";
    private int maxDescriptionLength = 200;
    private boolean includeResolvedIncidents = true;
    private boolean includeCompletedMaintenances = true;
    private boolean enableStatusAlerts = true;
    private String statusAlertRoleName = "Status Alerts";
    private boolean enableMaintenanceAlerts = false;

    public static Color hexToColor(String hexColor) {
        if (hexColor == null || hexColor.isEmpty()) {
            return Color.BLACK;
        }

        try {
            if (hexColor.startsWith("#")) {
                hexColor = hexColor.substring(1);
            }

            return new Color(Integer.parseInt(hexColor, 16));
        } catch (NumberFormatException e) {
            return Color.BLACK;
        }
    }

    public Color getOperationalColorObject() {
        return hexToColor(operationalColor);
    }

    public Color getDegradedColorObject() {
        return hexToColor(degradedColor);
    }

    public Color getPartialOutageColorObject() {
        return hexToColor(partialOutageColor);
    }

    public Color getMajorOutageColorObject() {
        return hexToColor(majorOutageColor);
    }

    public Color getMaintenanceColorObject() {
        return hexToColor(maintenanceColor);
    }
}
