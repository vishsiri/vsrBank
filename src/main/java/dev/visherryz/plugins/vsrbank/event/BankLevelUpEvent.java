package dev.visherryz.plugins.vsrbank.event;

import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Called when a player upgrades their bank tier
 */
@Getter
public class BankLevelUpEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerUuid;
    private final String playerName;
    private final int previousTier;
    private final int newTier;
    private final String newTierName;
    private final double upgradeCost;

    public BankLevelUpEvent(UUID playerUuid, String playerName,
                            int previousTier, int newTier, String newTierName,
                            double upgradeCost) {
        super(true); // Async event
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.previousTier = previousTier;
        this.newTier = newTier;
        this.newTierName = newTierName;
        this.upgradeCost = upgradeCost;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}