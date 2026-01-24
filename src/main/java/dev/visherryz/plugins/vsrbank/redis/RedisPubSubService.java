package dev.visherryz.plugins.vsrbank.redis;

import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.config.BankConfig;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.redisson.api.RTopic;
import org.redisson.api.listener.MessageListener;
import org.redisson.codec.SerializationCodec;

import java.io.Serializable;
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
    private int balanceListenerId;

    /**
     * Get balance topic name based on cluster ID
     */
    private String getBalanceTopic() {
        BankConfig config = plugin.getConfigManager().getConfig();
        String clusterId = config.getRedis().getClusterId();

        // Default to serverId if clusterId is empty
        if (clusterId == null || clusterId.isEmpty()) {
            clusterId = config.getServerId();
        }

        return "vsrbank:" + clusterId + ":balance";
    }

    /**
     * Initialize subscribers
     */
    public void initialize() {
        if (!redisManager.isConnected()) {
            plugin.getLogger().warning("Redis not connected, skipping pub/sub initialization");
            return;
        }

        String currentServerId = plugin.getConfigManager().getConfig().getServerId();
        String balanceTopicName = getBalanceTopic();

        // ✅ Use SerializationCodec instead of JsonJacksonCodec
        balanceTopic = redisManager.getRedisson().getTopic(balanceTopicName, new SerializationCodec());
        balanceListenerId = balanceTopic.addListener(BalanceUpdateMessage.class,
                (channel, message) -> {
                    // Ignore messages from this server
                    if (currentServerId.equals(message.sourceServer)) {
                        return;
                    }
                    handleBalanceUpdate(message);
                }
        );

        plugin.getLogger().info("✅ Subscribed to Redis topic: " + balanceTopicName);
    }

    /**
     * Shutdown subscribers
     */
    public void shutdown() {
        try {
            if (balanceTopic != null && balanceListenerId != 0) {
                balanceTopic.removeListener(balanceListenerId);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error shutting down pub/sub", e);
        }
    }

    /**
     * Publish balance update to all servers in cluster
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
     * Publish custom message to a specific channel
     */
    public void publish(String channel, Object message) {
        if (!redisManager.isConnected()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                // ✅ Use SerializationCodec
                RTopic topic = redisManager.getRedisson().getTopic(channel, new SerializationCodec());
                topic.publishAsync(message);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to publish message to " + channel, e);
            }
        });
    }

    /**
     * Subscribe to a custom topic
     */
    public <T> int subscribe(String channel, Class<T> messageType, MessageListener<T> listener) {
        if (!redisManager.isConnected()) {
            throw new IllegalStateException("Redis not connected");
        }

        // ✅ Use SerializationCodec
        RTopic topic = redisManager.getRedisson().getTopic(channel, new SerializationCodec());
        return topic.addListener(messageType, listener);
    }

    /**
     * Unsubscribe from a topic
     */
    public void unsubscribe(String channel, int listenerId) {
        if (!redisManager.isConnected()) {
            return;
        }

        RTopic topic = redisManager.getRedisson().getTopic(channel);
        topic.removeListener(listenerId);
    }

    /**
     * Handle balance update from another server
     */
    private void handleBalanceUpdate(BalanceUpdateMessage message) {
        UUID playerUuid = message.playerUuid;
        double newBalance = message.newBalance;

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                plugin.getLogger().fine("Received balance update from " + message.sourceServer +
                        " for " + player.getName() + ": " + newBalance);

                // Optional: Notify player
                // plugin.getMessageUtil().send(player, "Your balance was updated on another server!");
            }
        });
    }

    /**
     * Balance update message for pub/sub
     * ✅ Must implement Serializable for SerializationCodec
     */
    public static class BalanceUpdateMessage implements Serializable {
        private static final long serialVersionUID = 1L;

        public final UUID playerUuid;
        public final double newBalance;
        public final String sourceServer;

        public BalanceUpdateMessage(UUID playerUuid, double newBalance, String sourceServer) {
            this.playerUuid = playerUuid;
            this.newBalance = newBalance;
            this.sourceServer = sourceServer;
        }
    }
}