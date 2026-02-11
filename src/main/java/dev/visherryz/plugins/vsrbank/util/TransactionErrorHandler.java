package dev.visherryz.plugins.vsrbank.util;

import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.model.BankAccount;
import dev.visherryz.plugins.vsrbank.model.TransactionResponse;
import org.bukkit.entity.Player;

public class TransactionErrorHandler {

    private final VsrBank plugin;
    public TransactionErrorHandler(VsrBank plugin) {
        this.plugin = plugin;
    }

    public void handle(Player player, TransactionResponse response) {
        plugin.getLogger().info("DEBUG: Transaction Result for " + player.getName() + " is " + response.getResult());
        switch (response.getResult()) {
            case INSUFFICIENT_FUNDS, INSUFFICIENT_BANK_BALANCE ->
                    plugin.getMessageUtil().sendInsufficientFunds(player, response.getPreviousBalance());

            case MAX_BALANCE_REACHED, RECIPIENT_MAX_BALANCE ->
                    plugin.getMessageUtil().sendMaxBalanceReached(player,0);

            case COOLDOWN_ACTIVE ->
                    plugin.getMessageUtil().sendCooldownActive(player,
                            plugin.getBankService().getRemainingCooldown(player.getUniqueId()));

            case TRANSACTION_LOCKED ->
                    plugin.getMessageUtil().sendTransactionLocked(player);

            case BELOW_MINIMUM, INVALID_AMOUNT, ABOVE_MAXIMUM ->
                    plugin.getMessageUtil().sendInvalidAmount(player);

            case ACCOUNT_NOT_FOUND ->
                    plugin.getMessageUtil().sendNoAccount(player);

            case VAULT_NOT_AVAILABLE, VAULT_TRANSACTION_FAILED ->
                    plugin.getMessageUtil().sendDatabaseError(player);

            case RECIPIENT_NOT_FOUND, RECIPIENT_OFFLINE ->
                    plugin.getMessageUtil().sendPlayerNotFound(player, "");

            case CANNOT_TRANSFER_SELF ->
                    plugin.getMessageUtil().sendTransferToSelf(player);

            default ->
                    plugin.getMessageUtil().sendDatabaseError(player);
        }
    }
}
