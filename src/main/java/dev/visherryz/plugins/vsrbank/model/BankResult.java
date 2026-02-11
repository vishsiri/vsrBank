package dev.visherryz.plugins.vsrbank.model;

import lombok.Getter;

/**
 * Enum representing the result of bank operations
 * Used by the API and internally for clear status communication
 */
@Getter
public enum BankResult {

    /**
     * Operation completed successfully
     */
    SUCCESS(true, "Operation successful"),

    /**
     * Account doesn't have enough funds
     */
    INSUFFICIENT_FUNDS(false, "Insufficient funds in account"),

    /**
     * Insufficient funds in bank account (for withdrawals/transfers)
     */
    INSUFFICIENT_BANK_BALANCE(false, "Insufficient balance in bank"),

    /**
     * Deposit would exceed maximum balance for tier
     */
    MAX_BALANCE_REACHED(false, "Maximum balance limit reached"),

    /**
     * Recipient's balance would exceed maximum
     */
    RECIPIENT_MAX_BALANCE(false, "Recipient has reached maximum balance"),

    /**
     * Transaction is locked by Redis (another operation in progress)
     */
    TRANSACTION_LOCKED(false, "Transaction is currently locked"),

    /**
     * Database error occurred
     */
    DATABASE_ERROR(false, "Database error occurred"),

    /**
     * Account not found
     */
    ACCOUNT_NOT_FOUND(false, "Bank account not found"),

    /**
     * Invalid amount provided
     */
    INVALID_AMOUNT(false, "Invalid amount provided"),

    /**
     * Player is on cooldown
     */
    COOLDOWN_ACTIVE(false, "Transaction cooldown is active"),

    /**
     * Target player not found (for transfers)
     */
    TARGET_NOT_FOUND(false, "Target player not found"),

    /**
     * Recipient not found (for transfers)
     */
    RECIPIENT_NOT_FOUND(false, "Recipient not found"),

    /**
     * Recipient is offline
     */
    RECIPIENT_OFFLINE(false, "Recipient is offline"),

    /**
     * Cannot transfer to self
     */
    SELF_TRANSFER(false, "Cannot transfer to yourself"),

    /**
     * Cannot transfer to self (alternative name)
     */
    CANNOT_TRANSFER_SELF(false, "Cannot transfer to yourself"),

    /**
     * Offline transfers are disabled
     */
    OFFLINE_TRANSFER_DISABLED(false, "Offline transfers are disabled"),

    /**
     * Amount below minimum threshold
     */
    BELOW_MINIMUM(false, "Amount below minimum threshold"),

    /**
     * Amount above maximum threshold
     */
    ABOVE_MAXIMUM(false, "Amount above maximum threshold"),

    /**
     * Already at maximum tier
     */
    MAX_TIER_REACHED(false, "Already at maximum tier"),

    /**
     * Missing upgrade requirements (money or XP)
     */
    UPGRADE_REQUIREMENTS_NOT_MET(false, "Upgrade requirements not met"),

    /**
     * Not enough money for upgrade
     */
    INSUFFICIENT_MONEY_FOR_UPGRADE(false, "Not enough money for upgrade"),

    /**
     * Not enough XP for upgrade
     */
    INSUFFICIENT_XP_FOR_UPGRADE(false, "Not enough XP for upgrade"),

    /**
     * PlaceholderAPI requirements not met
     */
    REQUIREMENTS_NOT_MET(false, "Tier requirements not met"),

    /**
     * Permission denied
     */
    NO_PERMISSION(false, "Permission denied"),

    /**
     * Vault economy not available
     */
    VAULT_NOT_AVAILABLE(false, "Vault economy not available"),

    /**
     * Vault transaction failed
     */
    VAULT_TRANSACTION_FAILED(false, "Vault transaction failed"),

    /**
     * Unexpected error
     */
    UNEXPECTED_ERROR(false, "An unexpected error occurred");

    private final boolean success;
    private final String defaultMessage;

    BankResult(boolean success, String defaultMessage) {
        this.success = success;
        this.defaultMessage = defaultMessage;
    }

    /**
     * Check if this result represents a failure
     */
    public boolean isFailure() {
        return !success;
    }
}