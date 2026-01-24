package dev.visherryz.plugins.vsrbank.redis;

import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.config.BankConfig;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.redisson.api.RTopic;
import org.redisson.api.listener.MessageListener;
import org.redisson.codec.JsonJacksonCodec;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Redis Pub/Sub Service
 * Handles all messaging operations
 */
@RequiredArgsConstructor
public class RedisPubSubService {

    private final VsrBank plugin;
    private final RedisManager redisManager;

    private RTopic balanceTopic;
    private RTopic customTopic;

    private int balanceListenerId;
    private int customListenerId;

    private static final String BALANCE_TOPIC = "vsrbank:balance";

    /**
     * Initialize subscribers
     */
    public void initialize() {
        if (!redisManager.isConnected()) {
            plugin.getLogger().warning("Redis not connected, skipping pub/sub initialization");
            return;
        }

        String currentServerId = plugin.getConfigManager().getConfig().getServerId();
        BankConfig.RedisSettings settings = plugin.getConfigManager().getConfig().getRedis();

        // Balance update topic
        balanceTopic = redisManager.getRedisson().getTopic(BALANCE_TOPIC, new JsonJacksonCodec());
        balanceListenerId = balanceTopic.addListener(BalanceUpdateMessage.class,
                (channel, message) -> {
                    // Ignore messages from this server
                    if (currentServerId.equals(message.sourceServer())) {
                        return;
                    }
                    handleBalanceUpdate(message);
                }
        );

        // Custom topic
        customTopic = redisManager.getRedisson().getTopic(settings.getChannel(), new JsonJacksonCodec());
        customListenerId = customTopic.addListener(String.class,
                (channel, message) -> {
                    handleCustomMessage(message);
                }
        );

        plugin.getLogger().info("✅ Subscribed to Redis topics: " + BALANCE_TOPIC + ", " + settings.getChannel());
    }

    /**
     * Shutdown subscribers
     */
    public void shutdown() {
        try {
            if (balanceTopic != null && balanceListenerId != 0) {
                balanceTopic.removeListener(balanceListenerId);
            }
            if (customTopic != null && customListenerId != 0) {
                customTopic.removeListener(customListenerId);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error shutting down pub/sub", e);
        }
    }

    /**
     * Publish balance update to all servers
     */
    public void publishBalanceUpdate(UUID playerUuid, double newBalance) {
        if (!redisManager.isConnected()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                BalanceUpdateMessage message = new BalanceUpdateMessage(
                        playerUuid,
                        newBalance,
                        plugin.getConfigManager().getConfig().getServerId()
                );
                balanceTopic.publishAsync(message);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to publish balance update", e);
            }
        });
    }

    /**
     * Publish custom message
     */
    public void publish(String channel, Object message) {
        if (!redisManager.isConnected()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                RTopic topic = redisManager.getRedisson().getTopic(channel, new JsonJacksonCodec());
                topic.publishAsync(message);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to publish message", e);
            }
        });
    }

    /**
     * Subscribe to a topic
     */
    public <T> int subscribe(String channel, Class<T> messageType, MessageListener<T> listener) {
        RTopic topic = redisManager.getRedisson().getTopic(channel, new JsonJacksonCodec());
        return topic.addListener(messageType, listener);
    }

    /**
     * Unsubscribe from a topic
     */
    public void unsubscribe(String channel, int listenerId) {
        RTopic topic = redisManager.getRedisson().getTopic(channel);
        topic.removeListener(listenerId);
    }

    /**
     * Handle balance update from another server
     */
    private void handleBalanceUpdate(BalanceUpdateMessage message) {
        UUID playerUuid = message.playerUuid();
        double newBalance = message.newBalance();

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                plugin.getLogger().fine("Received balance update from " + message.sourceServer() +
                        " for " + player.getName() + ": " + newBalance);

                // Optional: Notify player
                // plugin.getMessageUtil().send(player, "Your balance was updated on another server!");
            }
        });
    }

    /**
     * Handle custom messages
     */
    private void handleCustomMessage(String message) {
        plugin.getLogger().fine("Received custom Redis message: " + message);
    }

    /**
     * Balance update message for pub/sub
     */
    public record BalanceUpdateMessage(UUID playerUuid, double newBalance, String sourceServer) {}
}