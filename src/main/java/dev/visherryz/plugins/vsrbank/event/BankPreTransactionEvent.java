package dev.visherryz.plugins.vsrbank.event;

import dev.visherryz.plugins.vsrbank.model.TransactionLog;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called BEFORE a bank transaction is processed
 * Can be cancelled to prevent the transaction
 */
@Getter
public class BankPreTransactionEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerUuid;
    private final String playerName;
    private final TransactionLog.TransactionType type;
    private final double amount;
    private final double currentBalance;

    @Setter
    private boolean cancelled = false;

    @Setter
    private String cancelReason = null;

    public BankPreTransactionEvent(UUID playerUuid, String playerName,
                                   TransactionLog.TransactionType type,
                                   double amount, double currentBalance) {
        super(true); // Async
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.type = type;
        this.amount = amount;
        this.currentBalance = currentBalance;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
