package net.hypixel.nerdbot.scout.webhook;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.scout.ScoutMetrics;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DiscordWebhook {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();

    private DiscordWebhook() {
    }

    /**
     * Sends a message with embeds to a Discord webhook URL.
     *
     * @param webhookUrl the full Discord webhook URL
     * @param content    optional text content (can be null)
     * @param embeds     list of embed JSON objects
     */
    public static void send(String webhookUrl, String content, List<JsonObject> embeds) {
        JsonObject payload = new JsonObject();

        if (content != null && !content.isBlank()) {
            payload.addProperty("content", content);
        }

        if (embeds != null && !embeds.isEmpty()) {
            JsonArray embedArray = new JsonArray();
            embeds.forEach(embedArray::add);
            payload.add("embeds", embedArray);
        }

        sendPayload(webhookUrl, payload);
    }

    /**
     * Sends a simple text message to a Discord webhook URL.
     *
     * @param webhookUrl the full Discord webhook URL
     * @param content    text content to send
     */
    public static void sendMessage(String webhookUrl, String content) {
        JsonObject payload = new JsonObject();
        payload.addProperty("content", content);
        sendPayload(webhookUrl, payload);
    }

    /**
     * Creates an embed JSON object matching the Discord embed structure.
     */
    public static JsonObject createEmbed(String title, String url, String description, int color) {
        JsonObject embed = new JsonObject();

        if (title != null) {
            embed.addProperty("title", title);
        }

        if (url != null) {
            embed.addProperty("url", url);
        }

        if (description != null) {
            embed.addProperty("description", description);
        }

        embed.addProperty("color", color);
        embed.addProperty("timestamp", java.time.Instant.now().toString());

        return embed;
    }

    /**
     * Adds a field to an embed JSON object.
     */
    public static void addField(JsonObject embed, String name, String value, boolean inline) {
        JsonArray fields;
        if (embed.has("fields")) {
            fields = embed.getAsJsonArray("fields");
        } else {
            fields = new JsonArray();
            embed.add("fields", fields);
        }

        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value);
        field.addProperty("inline", inline);
        fields.add(field);
    }

    private static void sendPayload(String webhookUrl, JsonObject payload) {
        RequestBody body = RequestBody.create(payload.toString(), JSON);
        Request request = new Request.Builder()
            .url(webhookUrl)
            .post(body)
            .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                ScoutMetrics.WEBHOOKS_SENT.labels("failure").inc();
                log.error("Failed to send webhook to {}: HTTP {} - {}", webhookUrl, response.code(), response.message());
            } else {
                ScoutMetrics.WEBHOOKS_SENT.labels("success").inc();
                log.debug("Successfully sent webhook to {}", webhookUrl);
            }
        } catch (IOException e) {
            ScoutMetrics.WEBHOOKS_SENT.labels("error").inc();
            log.error("Failed to send webhook to {}", webhookUrl, e);
        }
    }
}
