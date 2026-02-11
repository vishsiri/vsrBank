package dev.visherryz.plugins.vsrbank.gui;

import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.config.MessagesConfig;
import dev.visherryz.plugins.vsrbank.gui.v2.BankGuiV2;
import dev.visherryz.plugins.vsrbank.model.BankAccount;
import dev.visherryz.plugins.vsrbank.model.TransactionResponse;
import dev.visherryz.plugins.vsrbank.util.TransactionErrorHandler;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.conversations.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Dialog-based ChatInputHandler using Bukkit Conversations API
 * Updated to use BankGuiV2
 */
public class ChatInputHandler {

    private final VsrBank plugin;
    private final ConversationFactory depositFactory;
    private final ConversationFactory withdrawFactory;
    private final ConversationFactory transferPlayerFactory;

    public ChatInputHandler(VsrBank plugin) {
        this.plugin = plugin;

        // Create conversation factories
        this.depositFactory = new ConversationFactory(plugin)
                .withModality(true)
                .withTimeout(30)
                .withFirstPrompt(new AmountPrompt("deposit"))
                .withEscapeSequence("cancel")
                .addConversationAbandonedListener(this::onConversationAbandoned);

        this.withdrawFactory = new ConversationFactory(plugin)
                .withModality(true)
                .withTimeout(30)
                .withFirstPrompt(new AmountPrompt("withdraw"))
                .withEscapeSequence("cancel")
                .addConversationAbandonedListener(this::onConversationAbandoned);

        this.transferPlayerFactory = new ConversationFactory(plugin)
                .withModality(true)
                .withTimeout(30)
                .withFirstPrompt(new TransferPlayerPrompt())
                .withEscapeSequence("cancel")
                .addConversationAbandonedListener(this::onConversationAbandoned);
    }

    /**
     * Request amount input for deposit/withdraw
     */
    public void requestInput(Player player, String action) {
        if ("deposit".equalsIgnoreCase(action)) {
            depositFactory.buildConversation(player).begin();
        } else {
            withdrawFactory.buildConversation(player).begin();
        }
    }

    /**
     * Request player name for transfer
     */
    public void requestTransferPlayer(Player player) {
        transferPlayerFactory.buildConversation(player).begin();
    }

    /**
     * Request transfer amount after selecting player
     */
    public void requestTransferAmount(Player player, UUID targetUuid, String targetName) {
        ConversationFactory transferAmountFactory = new ConversationFactory(plugin)
                .withModality(true)
                .withTimeout(30)
                .withFirstPrompt(new TransferAmountPrompt(targetUuid, targetName))
                .withEscapeSequence("cancel")
                .addConversationAbandonedListener(this::onConversationAbandoned);

        transferAmountFactory.buildConversation(player).begin();
    }

    private void onConversationAbandoned(ConversationAbandonedEvent event) {
        if (!event.gracefulExit()) {
            Conversable conversable = event.getContext().getForWhom();
            if (conversable instanceof Player player) {
                if (event.getCanceller() instanceof ExactMatchConversationCanceller) {
                    plugin.getMessageUtil().send(player, plugin.getConfigManager().getMessages().getGuiInputCancelled());
                } else {
                    plugin.getMessageUtil().send(player, plugin.getConfigManager().getMessages().getGuiInputTimeout());
                }
                // เปลี่ยนเป็น BankGuiV2
                plugin.getServer().getScheduler().runTask(plugin, () -> new BankGuiV2(plugin).open(player));
            }
        }
    }

    // ==================== PROMPTS ====================

    /**
     * Amount input prompt for deposit/withdraw
     */
    private class AmountPrompt extends ValidatingPrompt {
        private final String action;

        public AmountPrompt(String action) {
            this.action = action;
        }

        @Override
        public @NotNull String getPromptText(@NotNull ConversationContext context) {
            String title = "deposit".equalsIgnoreCase(action)
                    ? plugin.getConfigManager().getMessages().getGuiDepositTitle()
                    : plugin.getConfigManager().getMessages().getGuiWithdrawTitle();

            // แสดง Title Alert
            Player player = (Player) context.getForWhom();
            showTitleAlert(player, action, null);

            return plugin.getMessageUtil().parsePlain("<yellow>" + title + "</yellow>\n<gray>Type the amount in chat, or type 'cancel' to cancel.</gray>");
        }

        @Override
        protected boolean isInputValid(@NotNull ConversationContext context, String input) {
            try {
                double amount = parseAmount(input.trim());
                return amount > 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        @Override
        protected String getFailedValidationText(@NotNull ConversationContext context, @NotNull String invalidInput) {
            return plugin.getMessageUtil().parsePlain(plugin.getConfigManager().getMessages().getInvalidAmount());
        }

        @Override
        protected Prompt acceptValidatedInput(ConversationContext context, String input) {
            Player player = (Player) context.getForWhom();
            double amount = parseAmount(input.trim());

            if ("deposit".equalsIgnoreCase(action)) {
                plugin.getBankService().deposit(player, amount, "Custom deposit")
                        .thenAccept(response -> handleTransactionResponse(player, response, amount, true));
            } else {
                plugin.getBankService().withdraw(player, amount, "Custom withdraw")
                        .thenAccept(response -> handleTransactionResponse(player, response, amount, false));
            }

            return END_OF_CONVERSATION;
        }
    }

    /**
     * Player name input prompt for transfer
     */
    private class TransferPlayerPrompt extends ValidatingPrompt {

        @Override
        public @NotNull String getPromptText(@NotNull ConversationContext context) {
            // แสดง Title Alert
            Player player = (Player) context.getForWhom();
            showTitleAlert(player, "transferPlayer", null);

            return plugin.getMessageUtil().parsePlain("<yellow>" + plugin.getConfigManager().getMessages().getGuiTransferPlayerTitle() + "</yellow>\n<gray>Type the player name in chat, or type 'cancel' to cancel.</gray>");
        }

        @Override
        protected boolean isInputValid(@NotNull ConversationContext context, String input) {
            return !input.trim().isEmpty();
        }

        @Override
        protected String getFailedValidationText(@NotNull ConversationContext context, @NotNull String invalidInput) {
            return plugin.getMessageUtil().parsePlain("<red>Please enter a valid player name!</red>");
        }

        @Override
        protected Prompt acceptValidatedInput(ConversationContext context, String input) {
            Player player = (Player) context.getForWhom();
            String rawInput = input.trim();

            OfflinePlayer target = Bukkit.getOfflinePlayer(rawInput);
            String correctName = target.getName() != null ? target.getName() : rawInput;

            plugin.getBankService().getAccountByName(correctName).thenAccept(optAccount -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (optAccount.isEmpty()) {
                        plugin.getMessageUtil().sendTargetNoAccount(player, correctName);
                        new BankGuiV2(plugin).open(player);
                        return;
                    }

                    BankAccount account = optAccount.get();
                    if (account.getUuid().equals(player.getUniqueId())) {
                        plugin.getMessageUtil().sendTransferToSelf(player);
                        new BankGuiV2(plugin).open(player);
                        return;
                    }

                    requestTransferAmount(player, account.getUuid(), account.getPlayerName());
                });
            });

            return END_OF_CONVERSATION;
        }
    }

    /**
     * Transfer amount prompt
     */
    private class TransferAmountPrompt extends ValidatingPrompt {
        private final UUID targetUuid;
        private final String targetName;

        public TransferAmountPrompt(UUID targetUuid, String targetName) {
            this.targetUuid = targetUuid;
            this.targetName = targetName;
        }

        @Override
        public @NotNull String getPromptText(@NotNull ConversationContext context) {
            // แสดง Title Alert
            Player player = (Player) context.getForWhom();
            showTitleAlert(player, "transferAmount", targetName);

            return plugin.getMessageUtil().parsePlain("<yellow>Enter amount to transfer to <white>" + targetName + "</white>:</yellow>\n<gray>Type the amount in chat, or type 'cancel' to cancel.</gray>");
        }

        @Override
        protected boolean isInputValid(@NotNull ConversationContext context, String input) {
            try {
                double amount = parseAmount(input.trim());
                return amount > 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        @Override
        protected String getFailedValidationText(@NotNull ConversationContext context, @NotNull String invalidInput) {
            return plugin.getMessageUtil().parsePlain(plugin.getConfigManager().getMessages().getInvalidAmount());
        }

        @Override
        protected Prompt acceptValidatedInput(ConversationContext context, String input) {
            Player player = (Player) context.getForWhom();
            double amount = parseAmount(input.trim());

            plugin.getBankService().transfer(player, targetName, amount)
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

            return END_OF_CONVERSATION;
        }
    }

    // ==================== HELPER METHODS ====================

    private void handleTransactionResponse(Player player, TransactionResponse response, double amount, boolean isDeposit) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (response.isSuccess()) {
                if (isDeposit) {
                    plugin.getMessageUtil().sendDepositSuccess(player, response.getProcessedAmount(), response.getNewBalance());
                } else {
                    plugin.getMessageUtil().sendWithdrawSuccess(player, response.getProcessedAmount(), response.getNewBalance());
                }
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            } else {
                new TransactionErrorHandler(plugin).handle(player, response);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
        });
    }

    private double parseAmount(String input) throws NumberFormatException {
        // Security: ป้องกัน input ยาวเกินไป (DoS attack)
        if (input == null || input.length() > 50) {
            throw new NumberFormatException("Input too long");
        }

        // Clean input
        input = input.toLowerCase().replace(",", "").replace(" ", "").trim();

        // Security: ป้องกัน empty string
        if (input.isEmpty()) {
            throw new NumberFormatException("Empty input");
        }

        // Security: ป้องกันค่าติดลบ (critical!)
        if (input.startsWith("-")) {
            throw new NumberFormatException("Negative values not allowed");
        }

        // Parse multiplier
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

        // Security: ตรวจสอบว่า input ที่เหลือเป็นตัวเลขที่ valid
        if (input.isEmpty() || !input.matches("^[0-9]+(\\.[0-9]+)?$")) {
            throw new NumberFormatException("Invalid number format");
        }

        double parsed = Double.parseDouble(input);
        double result = parsed * multiplier;

        // Security: ป้องกัน overflow และค่าสูงเกินไป
        if (Double.isInfinite(result) || Double.isNaN(result)) {
            throw new NumberFormatException("Value too large");
        }

        // Security: กำหนดค่าสูงสุด (1 quadrillion = 1,000 trillion)
        double MAX_AMOUNT = 1_000_000_000_000_000.0; // 1 quadrillion
        if (result > MAX_AMOUNT) {
            throw new NumberFormatException("Amount exceeds maximum limit");
        }

        // Security: ป้องกันค่าติดลบอีกครั้ง (double check)
        if (result <= 0) {
            throw new NumberFormatException("Amount must be positive");
        }

        return result;
    }

    /**
     * แสดง Title Alert ให้ Player เมื่อต้องใส่ Input
     * @param player Player ที่จะแสดง Title
     * @param action ประเภทของ action (deposit, withdraw, transferPlayer, transferAmount)
     * @param targetName ชื่อผู้เล่นเป้าหมาย (สำหรับ transferAmount)
     */
    private void showTitleAlert(Player player, String action, String targetName) {
        String mainTitle = "";
        String subTitle = "";

        MessagesConfig messages = plugin.getConfigManager().getMessages();

        switch (action.toLowerCase()) {
            case "deposit":
                mainTitle = messages.getTitleDepositMain();
                subTitle = messages.getTitleDepositSub();
                break;
            case "withdraw":
                mainTitle = messages.getTitleWithdrawMain();
                subTitle = messages.getTitleWithdrawSub();
                break;
            case "transferplayer":
                mainTitle = messages.getTitleTransferPlayerMain();
                subTitle = messages.getTitleTransferPlayerSub();
                break;
            case "transferamount":
                mainTitle = messages.getTitleTransferAmountMain().replace("{player}", targetName);
                subTitle = messages.getTitleTransferAmountSub();
                break;
        }

        // Parse MiniMessage และแสดง Title
        String parsedMain = plugin.getMessageUtil().parsePlain(mainTitle);
        String parsedSub = plugin.getMessageUtil().parsePlain(subTitle);

        player.sendTitle(parsedMain, parsedSub, 10, 200, 10);
    }
}