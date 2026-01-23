package dev.visherryz.plugins.vsrbank.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a player's bank account
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccount {

    /**
     * Player's UUID
     */
    private UUID uuid;

    /**
     * Player's last known name
     */
    private String playerName;

    /**
     * Current balance in bank
     */
    private double balance;

    /**
     * Current bank tier level (1-5)
     */
    private int tier;

    /**
     * Total interest earned
     */
    private double totalInterestEarned;

    /**
     * Last time interest was calculated
     */
    private Instant lastInterestTime;

    /**
     * Last time the player was online
     */
    private Instant lastOnline;

    /**
     * Account creation timestamp
     */
    private Instant createdAt;

    /**
     * Last update timestamp
     */
    private Instant updatedAt;

    /**
     * Check if this account can receive more money
     * @param amount Amount to check
     * @param maxBalance Maximum allowed balance for this tier (-1 for unlimited)
     * @return true if the deposit would not exceed max balance
     */
    public boolean canDeposit(double amount, double maxBalance) {
        if (maxBalance < 0) return true; // -1 means unlimited
        return (balance + amount) <= maxBalance;
    }

    /**
     * Check if this account has sufficient funds
     * @param amount Amount to check
     * @return true if balance is sufficient
     */
    public boolean hasBalance(double amount) {
        return balance >= amount;
    }

    /**
     * Create a new default account for a player
     */
    public static BankAccount createNew(UUID uuid, String playerName) {
        Instant now = Instant.now();
        return BankAccount.builder()
                .uuid(uuid)
                .playerName(playerName)
                .balance(0.0)
                .tier(1)
                .totalInterestEarned(0.0)
                .lastInterestTime(now)
                .lastOnline(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}