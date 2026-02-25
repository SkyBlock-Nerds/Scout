package net.hypixel.nerdbot.scout.handler.firesale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.marmalade.format.DiscordTimestamp;
import net.hypixel.nerdbot.marmalade.json.JsonUtils;
import net.hypixel.nerdbot.marmalade.format.StringUtils;
import net.hypixel.nerdbot.marmalade.Tuple;
import net.hypixel.nerdbot.scout.Main;
import org.jetbrains.annotations.Nullable;
import net.hypixel.nerdbot.scout.ScoutMetrics;
import net.hypixel.nerdbot.scout.webhook.DiscordWebhook;
import net.hypixel.nerdbot.scout.watcher.URLWatcher;

import java.awt.Color;
import java.util.List;

@Slf4j
public class FireSaleDataHandler implements URLWatcher.DataHandler {

    @Override
    public void handleData(@Nullable String oldContent, String newContent, List<Tuple<String, Object, Object>> changedValues) {
        log.info("Fire sale data changed!");
        log.debug("Changed values: " + changedValues);
        ScoutMetrics.DATA_CHANGES_DETECTED.labels("fire-sales").inc();

        JsonArray oldSaleData = JsonUtils.parseString(oldContent).getAsJsonObject().getAsJsonArray("sales");
        JsonArray newSaleData = JsonUtils.parseString(newContent).getAsJsonObject().getAsJsonArray("sales");

        for (int i = 0; i < oldSaleData.size(); i++) {
            for (int j = 0; j < newSaleData.size(); j++) {
                JsonObject oldObject = oldSaleData.get(i).getAsJsonObject();
                JsonObject newObject = newSaleData.get(j).getAsJsonObject();

                if (isEqual(oldObject, newObject)) {
                    newSaleData.remove(j);
                    log.debug("Removed " + oldSaleData.get(i).getAsJsonObject().get("item_id").getAsString() + " from the new sale data list.");
                    break;
                }
            }
        }

        if (newSaleData.isEmpty()) {
            log.info("No new sale data found!");
            return;
        }

        JsonObject embed = DiscordWebhook.createEmbed("New Fire Sale!", null, null, Color.GREEN.getRGB() & 0xFFFFFF);

        newSaleData.asList().stream()
            .map(JsonElement::getAsJsonObject)
            .forEachOrdered(jsonObject -> {
                String itemId = jsonObject.get("item_id").getAsString();
                DiscordTimestamp startTime = new DiscordTimestamp(jsonObject.get("start").getAsLong());
                DiscordTimestamp endTime = new DiscordTimestamp(jsonObject.get("end").getAsLong());
                int amount = jsonObject.get("amount").getAsInt();
                int price = jsonObject.get("price").getAsInt();

                log.info("Found new sale data for item " + itemId + "!");

                String fieldValue = "Start Time: " + startTime.toLongDateTime() +
                    " (" + startTime.toRelativeTimestamp() + ")" + "\n" +
                    "End Time: " + endTime.toLongDateTime() +
                    " (" + endTime.toRelativeTimestamp() + ")" + "\n" +
                    "Amount: " + StringUtils.COMMA_SEPARATED_FORMAT.format(amount) + "x\n" +
                    "Price: " + StringUtils.COMMA_SEPARATED_FORMAT.format(price) + " SkyBlock Gems";

                DiscordWebhook.addField(embed, itemId, fieldValue, false);
            });

        // Look up webhook URL from the active watcher config
        String webhookUrl = Main.getWebhookUrlForHandler(this.getClass().getName());
        String alertRoleId = Main.getAlertRoleIdForHandler(this.getClass().getName());

        if (webhookUrl != null) {
            String content = null;
            if (alertRoleId != null && !alertRoleId.isBlank()) {
                content = "<@&" + alertRoleId + ">";
            }

            DiscordWebhook.send(webhookUrl, content, List.of(embed));
        } else {
            log.warn("No webhook URL configured for fire sale handler");
        }
    }

    private boolean isEqual(JsonObject oldObject, JsonObject newObject) {
        return oldObject.get("item_id").getAsString().equals(newObject.get("item_id").getAsString());
    }
}
