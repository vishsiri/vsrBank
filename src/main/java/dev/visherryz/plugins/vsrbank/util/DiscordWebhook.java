package dev.visherryz.plugins.vsrbank.util;

import com.google.gson.JsonObject;
import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.config.BankConfig;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Discord Webhook integration for admin notifications and alerts
 */
public class DiscordWebhook {

    private final VsrBank plugin;
    private final ExecutorService executor;
    private final DecimalFormat currencyFormat;

    public DiscordWebhook(VsrBank plugin) {
        this.plugin = plugin;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "VsrBank-Discord-Webhook");
            t.setDaemon(true);
            return t;
        });
        this.currencyFormat = new DecimalFormat("#,##0.00");
    }

    /**
     * Shutdown executor
     */
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * Get settings
     */
    private BankConfig.DiscordSettings getSettings() {
        return plugin.getConfigManager().getConfig().getDiscord();
    }

    /**
     * Send admin action notification
     */
    public void sendAdminAction(String adminName, String action, String targetPlayer, double amount) {
        BankConfig.DiscordSettings settings = getSettings();

        if (!settings.isEnabled() || !settings.isNotifyAdminCommands()) {
            return;
        }

        String webhookUrl = settings.getAdminWebhookUrl();
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return;
        }

        String symbol = plugin.getConfigManager().getConfig().getEconomy().getCurrencySymbol();
        String serverId = plugin.getConfigManager().getConfig().getServerId();

        CompletableFuture.runAsync(() -> {
            try {
                JsonObject embed = new JsonObject();
                embed.addProperty("title", "🔧 Admin Action");
                embed.addProperty("color", 16776960); // Yellow

                StringBuilder description = new StringBuilder();
                description.append("**Admin:** ").append(adminName).append("\n");
                description.append("**Action:** ").append(action).append("\n");
                description.append("**Target:** ").append(targetPlayer).append("\n");
                description.append("**Amount:** ").append(symbol).append(currencyFormat.format(amount)).append("\n");
                description.append("**Server:** ").append(serverId);

                embed.addProperty("description", description.toString());
                embed.addProperty("timestamp", Instant.now().toString());

                sendWebhook(webhookUrl, embed);

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to send Discord webhook", e);
            }
        }, executor);
    }

    /**
     * Send suspicious activity alert
     */
    public void sendSuspiciousAlert(String playerName, String action, double amount, String details) {
        BankConfig.DiscordSettings settings = getSettings();

        if (!settings.isEnabled()) {
            return;
        }

        String webhookUrl = settings.getAlertWebhookUrl();
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            webhookUrl = settings.getAdminWebhookUrl(); // Fallback to admin webhook
        }

        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return;
        }

        String symbol = plugin.getConfigManager().getConfig().getEconomy().getCurrencySymbol();
        String serverId = plugin.getConfigManager().getConfig().getServerId();

        final String finalWebhookUrl = webhookUrl;
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject embed = new JsonObject();
                embed.addProperty("title", "⚠️ Suspicious Activity Detected");
                embed.addProperty("color", 16711680); // Red

                StringBuilder description = new StringBuilder();
                description.append("**Player:** ").append(playerName).append("\n");
                description.append("**Action:** ").append(action).append("\n");
                description.append("**Amount:** ").append(symbol).append(currencyFormat.format(amount)).append("\n");
                description.append("**Server:** ").append(serverId).append("\n");
                if (details != null && !details.isEmpty()) {
                    description.append("**Details:** ").append(details);
                }

                embed.addProperty("description", description.toString());
                embed.addProperty("timestamp", Instant.now().toString());

                sendWebhook(finalWebhookUrl, embed);

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to send Discord alert", e);
            }
        }, executor);
    }

    /**
     * Send a webhook request
     */
    private void sendWebhook(String webhookUrl, JsonObject embed) throws Exception {
        JsonObject payload = new JsonObject();
        payload.addProperty("username", "VsrBank");

        com.google.gson.JsonArray embeds = new com.google.gson.JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);

        URL url = new URL(webhookUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != 200 && responseCode != 204) {
            plugin.getLogger().warning("Discord webhook returned code: " + responseCode);
        }

        connection.disconnect();
    }

    /**
     * Check if a transaction should trigger suspicious alert
     */
    public void checkSuspiciousTransaction(String playerName, String action, double amount) {
        BankConfig.DiscordSettings settings = getSettings();

        if (!settings.isEnabled()) {
            return;
        }

        if (amount >= settings.getSuspiciousThreshold()) {
            sendSuspiciousAlert(playerName, action, amount, "Amount exceeds threshold");
        }
    }
}