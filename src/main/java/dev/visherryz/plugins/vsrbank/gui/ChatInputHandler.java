package dev.visherryz.plugins.vsrbank.gui;

import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.model.BankAccount;
import dev.visherryz.plugins.vsrbank.model.TransactionResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles chat input for custom amounts and player names
 */
public class ChatInputHandler {

    private final VsrBank plugin;

    // Pending inputs: player UUID -> input type
    private final Map<UUID, InputSession> pendingSessions = new ConcurrentHashMap<>();

    public ChatInputHandler(VsrBank plugin) {
        this.plugin = plugin;
    }

    /**
     * Request amount input for deposit/withdraw
     */
    public void requestInput(Player player, String action) {
        String title = "deposit".equalsIgnoreCase(action) ?
                plugin.getConfigManager().getMessages().getGuiDepositTitle() :
                plugin.getConfigManager().getMessages().getGuiWithdrawTitle();

        plugin.getMessageUtil().sendRaw(player, "<yellow>" + title + "</yellow>");
        plugin.getMessageUtil().sendRaw(player, "<gray>Type the amount in chat, or type 'cancel' to cancel.</gray>");

        InputSession session = new InputSession(InputType.AMOUNT, action, null, null);
        startSession(player, session);
    }

    /**
     * Request player name for transfer
     */
    public void requestTransferPlayer(Player player) {
        plugin.getMessageUtil().sendRaw(player, "<yellow>" + plugin.getConfigManager().getMessages().getGuiTransferPlayerTitle() + "</yellow>");
        plugin.getMessageUtil().sendRaw(player, "<gray>Type the player name in chat, or type 'cancel' to cancel.</gray>");

        InputSession session = new InputSession(InputType.PLAYER_NAME, "transfer", null, null);
        startSession(player, session);
    }

    /**
     * Request transfer amount after selecting player
     */
    public void requestTransferAmount(Player player, UUID targetUuid, String targetName) {
        plugin.getMessageUtil().sendRaw(player, "<yellow>Enter amount to transfer to <white>" + targetName + "</white>:</yellow>");
        plugin.getMessageUtil().sendRaw(player, "<gray>Type the amount in chat, or type 'cancel' to cancel.</gray>");

        InputSession session = new InputSession(InputType.TRANSFER_AMOUNT, "transfer", targetUuid, targetName);
        startSession(player, session);
    }

    public void clearAllSessions() {
        pendingSessions.values().forEach(session -> {
            if (session.listener != null) {
                HandlerList.unregisterAll(session.listener);
            }
            if (session.timeoutTask != null) {
                session.timeoutTask.cancel();
            }
        });
        pendingSessions.clear();
    }

    private void startSession(Player player, InputSession session) {
        // Cancel any existing session
        InputSession existing = pendingSessions.remove(player.getUniqueId());
        if (existing != null && existing.listener != null) {
            HandlerList.unregisterAll(existing.listener);
        }
        if (existing != null && existing.timeoutTask != null) {
            existing.timeoutTask.cancel();
        }

        // Create listener
        ChatListener listener = new ChatListener(player.getUniqueId());
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        session.listener = listener;

        // Set timeout (30 seconds)
        session.timeoutTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            InputSession expired = pendingSessions.remove(player.getUniqueId());
            if (expired != null) {
                if (expired.listener != null) {
                    HandlerList.unregisterAll(expired.listener);
                }
                plugin.getMessageUtil().send(player, plugin.getConfigManager().getMessages().getGuiInputTimeout());
                new BankGui(plugin).open(player);
            }
        }, 30 * 20L); // 30 seconds

        pendingSessions.put(player.getUniqueId(), session);
    }

    private void handleInput(Player player, String input) {
        InputSession session = pendingSessions.remove(player.getUniqueId());
        if (session == null) return;

        // Cleanup
        if (session.listener != null) {
            HandlerList.unregisterAll(session.listener);
        }
        if (session.timeoutTask != null) {
            session.timeoutTask.cancel();
        }

        // Check for cancel
        if ("cancel".equalsIgnoreCase(input.trim())) {
            plugin.getMessageUtil().send(player, plugin.getConfigManager().getMessages().getGuiInputCancelled());
            plugin.getServer().getScheduler().runTask(plugin, () -> new BankGui(plugin).open(player));
            return;
        }

        switch (session.type) {
            case AMOUNT -> handleAmountInput(player, input, session.action);
            case PLAYER_NAME -> handlePlayerNameInput(player, input);
            case TRANSFER_AMOUNT -> handleTransferAmountInput(player, input, session.targetUuid, session.targetName);
        }
    }

    private void handleAmountInput(Player player, String input, String action) {
        double amount;
        try {
            // Support formats like "1k", "1.5m"
            amount = parseAmount(input.trim());
        } catch (NumberFormatException e) {
            plugin.getMessageUtil().sendInvalidAmount(player);
            plugin.getServer().getScheduler().runTask(plugin, () -> new BankGui(plugin).open(player));
            return;
        }

        if (amount <= 0) {
            plugin.getMessageUtil().send(player, plugin.getConfigManager().getMessages().getMustBePositive());
            plugin.getServer().getScheduler().runTask(plugin, () -> new BankGui(plugin).open(player));
            return;
        }

        if ("deposit".equalsIgnoreCase(action)) {
            plugin.getBankService().deposit(player, amount, "Custom deposit")
                    .thenAccept(response -> handleTransactionResponse(player, response, amount, true));
        } else {
            plugin.getBankService().withdraw(player, amount, "Custom withdraw")
                    .thenAccept(response -> handleTransactionResponse(player, response, amount, false));
        }
    }

    private void handlePlayerNameInput(Player player, String input) {
        String rawInput = input.trim();

        OfflinePlayer target = Bukkit.getOfflinePlayer(rawInput);
        String correctName = target.getName();

        if (correctName == null) {
            correctName = rawInput;
        }
        String finalTargetName = correctName;

        plugin.getBankService().getAccountByName(finalTargetName).thenAccept(optAccount -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (optAccount.isEmpty()) {
                    plugin.getMessageUtil().sendTargetNoAccount(player, finalTargetName);
                    new BankGui(plugin).open(player);
                    return;
                }

                BankAccount account = optAccount.get();
                if (account.getUuid().equals(player.getUniqueId())) {
                    plugin.getMessageUtil().sendTransferToSelf(player);
                    new BankGui(plugin).open(player);
                    return;
                }

                requestTransferAmount(player, account.getUuid(), account.getPlayerName());
            });
        });
    }

    private void handleTransferAmountInput(Player player, String input, UUID targetUuid, String targetName) {
        double amount;
        try {
            amount = parseAmount(input.trim());
        } catch (NumberFormatException e) {
            plugin.getMessageUtil().sendInvalidAmount(player);
            plugin.getServer().getScheduler().runTask(plugin, () -> new BankGui(plugin).open(player));
            return;
        }

        if (amount <= 0) {
            plugin.getMessageUtil().send(player, plugin.getConfigManager().getMessages().getMustBePositive());
            plugin.getServer().getScheduler().runTask(plugin, () -> new BankGui(plugin).open(player));
            return;
        }

        plugin.getBankService().transfer(player, targetUuid, targetName, amount, "GUI transfer")
                .thenAccept(response -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (response.isSuccess()) {
                            plugin.getMessageUtil().sendTransferSuccess(player, amount, targetName);
                            if (response.getFee() > 0) {
                                plugin.getMessageUtil().sendTransferFee(player, response.getFee());
                            }
                            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                        } else {
                            switch (response.getResult()) {
                                case INSUFFICIENT_FUNDS -> plugin.getMessageUtil().sendInsufficientFunds(player, response.getPreviousBalance());
                                case TARGET_NOT_FOUND -> plugin.getMessageUtil().sendTargetNoAccount(player, targetName);
                                case SELF_TRANSFER -> plugin.getMessageUtil().sendTransferToSelf(player);
                                case BELOW_MINIMUM -> plugin.getMessageUtil().sendTransferMinimum(player,
                                        plugin.getConfigManager().getConfig().getTransaction().getMinTransferAmount());
                                case ABOVE_MAXIMUM -> plugin.getMessageUtil().sendTransferMaximum(player,
                                        plugin.getConfigManager().getConfig().getTransaction().getMaxTransferAmount());
                                default -> plugin.getMessageUtil().sendTransferFailed(player);
                            }
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        }
                    });
                });
    }

    private void handleTransactionResponse(Player player, TransactionResponse response, double amount, boolean isDeposit) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (response.isSuccess()) {
                if (isDeposit) {
                    plugin.getMessageUtil().sendDepositSuccess(player, amount, response.getNewBalance());
                } else {
                    plugin.getMessageUtil().sendWithdrawSuccess(player, amount, response.getNewBalance());
                }
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            } else {
                switch (response.getResult()) {
                    case INSUFFICIENT_FUNDS -> plugin.getMessageUtil().sendInsufficientFunds(player, response.getPreviousBalance());
                    case MAX_BALANCE_REACHED -> plugin.getMessageUtil().sendMaxBalanceReached(player,
                            plugin.getConfigManager().getConfig().getTier(1).getMaxBalance());
                    case COOLDOWN_ACTIVE -> plugin.getMessageUtil().sendCooldownActive(player,
                            plugin.getBankService().getRemainingCooldown(player.getUniqueId()));
                    default -> plugin.getMessageUtil().sendDatabaseError(player);
                }
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
        });
    }

    /**
     * Parse amount with K/M/B suffixes
     */
    private double parseAmount(String input) throws NumberFormatException {
        input = input.toLowerCase().replace(",", "").replace(" ", "");

        double multiplier = 1;
        if (input.endsWith("k")) {
            multiplier = 1000;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("m")) {
            multiplier = 1_000_000;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("b")) {
            multiplier = 1_000_000_000;
            input = input.substring(0, input.length() - 1);
        }

        return Double.parseDouble(input) * multiplier;
    }

    // ==================== Inner Classes ====================

    private enum InputType {
        AMOUNT, PLAYER_NAME, TRANSFER_AMOUNT
    }

    private static class InputSession {
        final InputType type;
        final String action;
        final UUID targetUuid;
        final String targetName;
        Listener listener;
        BukkitTask timeoutTask;

        InputSession(InputType type, String action, UUID targetUuid, String targetName) {
            this.type = type;
            this.action = action;
            this.targetUuid = targetUuid;
            this.targetName = targetName;
        }
    }

    private class ChatListener implements Listener {
        private final UUID playerUuid;

        ChatListener(UUID playerUuid) {
            this.playerUuid = playerUuid;
        }

        @EventHandler(priority = EventPriority.LOWEST)
        @SuppressWarnings("deprecation")
        public void onChat(AsyncPlayerChatEvent event) {
            if (!event.getPlayer().getUniqueId().equals(playerUuid)) return;
            if (!pendingSessions.containsKey(playerUuid)) return;

            event.setCancelled(true);
            handleInput(event.getPlayer(), event.getMessage());
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            if (!event.getPlayer().getUniqueId().equals(playerUuid)) return;

            InputSession session = pendingSessions.remove(playerUuid);
            if (session != null) {
                if (session.timeoutTask != null) {
                    session.timeoutTask.cancel();
                }
                HandlerList.unregisterAll(this);
            }
        }
    }
}