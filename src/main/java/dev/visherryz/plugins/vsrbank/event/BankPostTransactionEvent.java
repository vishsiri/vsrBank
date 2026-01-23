package dev.visherryz.plugins.vsrbank.event;

import dev.visherryz.plugins.vsrbank.model.TransactionLog; // Import ตัวนี้
import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@Getter
public class BankPostTransactionEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerUuid;
    private final String playerName;

    // แก้จาก BankPreTransactionEvent.TransactionType เป็น TransactionLog.TransactionType
    private final TransactionLog.TransactionType type;

    private final double amount;
    private final double previousBalance;
    private final double newBalance;

    public BankPostTransactionEvent(UUID playerUuid, String playerName,
                                    TransactionLog.TransactionType type, // แก้ตรงนี้ด้วย
                                    double amount, double previousBalance, double newBalance) {
        super(true);
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.type = type;
        this.amount = amount;
        this.previousBalance = previousBalance;
        this.newBalance = newBalance;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public double getBalanceChange() {
        return newBalance - previousBalance;
    }
}