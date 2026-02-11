package dev.visherryz.plugins.vsrbank.model;

import lombok.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Represents a transaction log entry
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionLog {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm")
            .withZone(ZoneId.systemDefault());

    /**
     * Unique transaction ID
     */
    private long id;

    /**
     * Player who initiated the transaction
     */
    private UUID playerUuid;

    /**
     * Player name at time of transaction
     */
    private String playerName;

    /**
     * Type of transaction
     */
    private TransactionType type;

    /**
     * Amount involved in transaction
     */
    private double amount;

    /**
     * Balance before transaction
     */
    private double balanceBefore;

    /**
     * Balance after transaction
     */
    private double balanceAfter;

    /**
     * Target player UUID (for transfers)
     */
    private UUID targetUuid;

    /**
     * Target player name (for transfers)
     */
    private String targetName;

    /**
     * Server where transaction occurred
     */
    private String serverId;

    /**
     * Additional notes/reason
     */
    private String reason;

    /**
     * Whether this was an admin action
     */
    private boolean adminAction;

    /**
     * Admin who performed the action (if adminAction)
     */
    private String adminName;

    /**
     * Timestamp of transaction
     */
    private Instant timestamp;

    /**
     * Transaction types
     */
    @Getter
    public enum TransactionType {
        DEPOSIT("Deposit", "➕", "GREEN_WOOL"),
        WITHDRAW("Withdraw", "➖", "RED_WOOL"),
        TRANSFER_OUT("Transfer Out", "📤", "ORANGE_WOOL"),
        TRANSFER_IN("Transfer In", "📥", "LIME_WOOL"),
        INTEREST("Interest", "💰", "GOLD_INGOT"),
        ADMIN_GIVE("Admin Give", "🎁", "EMERALD"),
        ADMIN_TAKE("Admin Take", "🔧", "REDSTONE"),
        ADMIN_SET("Admin Set", "⚙️", "COMMAND_BLOCK"),
        UPGRADE("Upgrade", "⬆️", "NETHER_STAR"),
        FEE("Fee", "💸", "IRON_NUGGET");

        private final String displayName;
        private final String icon;
        private final String material;

        TransactionType(String displayName, String icon, String material) {
            this.displayName = displayName;
            this.icon = icon;
            this.material = material;
        }

    }

    /**
     * Get formatted date string
     */
    public String getFormattedDate() {
        return DATE_FORMAT.format(timestamp);
    }

    /**
     * Get formatted description for GUI display
     */
    public String getFormattedDescription() {
        return switch (type) {
            case DEPOSIT, WITHDRAW, INTEREST ->
                    String.format("%s %s: $%.2f", type.getIcon(), type.getDisplayName(), amount);
            case TRANSFER_OUT ->
                    String.format("%s Sent to %s: $%.2f", type.getIcon(), targetName, amount);
            case TRANSFER_IN ->
                    String.format("%s From %s: $%.2f", type.getIcon(), targetName, amount);
            case ADMIN_SET ->
                    String.format("%s %s by %s: Set to $%.2f", type.getIcon(), type.getDisplayName(), adminName, amount);
            case ADMIN_GIVE, ADMIN_TAKE ->
                    String.format("%s %s by %s: $%.2f", type.getIcon(), type.getDisplayName(), adminName, amount);
            case UPGRADE ->
                    String.format("%s Tier Upgrade: -$%.2f", type.getIcon(), amount);
            case FEE ->
                    String.format("%s Fee: $%.2f", type.getIcon(), amount);
        };
    }

    /**
     * Create a new log entry for deposit
     */
    public static TransactionLog deposit(UUID playerUuid, String playerName, double amount,
                                         double balanceBefore, double balanceAfter, String serverId) {
        return TransactionLog.builder()
                .playerUuid(playerUuid)
                .playerName(playerName)
                .type(TransactionType.DEPOSIT)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .serverId(serverId)
                .adminAction(false)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a new log entry for withdrawal
     */
    public static TransactionLog withdraw(UUID playerUuid, String playerName, double amount,
                                          double balanceBefore, double balanceAfter, String serverId) {
        return TransactionLog.builder()
                .playerUuid(playerUuid)
                .playerName(playerName)
                .type(TransactionType.WITHDRAW)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .serverId(serverId)
                .adminAction(false)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create log entries for transfer (returns array: [sender log, receiver log])
     */
    public static TransactionLog[] transfer(UUID senderUuid, String senderName, double senderBalanceBefore, double senderBalanceAfter,
                                            UUID receiverUuid, String receiverName, double receiverBalanceBefore, double receiverBalanceAfter,
                                            double amount, String serverId) {
        Instant now = Instant.now();

        TransactionLog senderLog = TransactionLog.builder()
                .playerUuid(senderUuid)
                .playerName(senderName)
                .type(TransactionType.TRANSFER_OUT)
                .amount(amount)
                .balanceBefore(senderBalanceBefore)
                .balanceAfter(senderBalanceAfter)
                .targetUuid(receiverUuid)
                .targetName(receiverName)
                .serverId(serverId)
                .adminAction(false)
                .timestamp(now)
                .build();

        TransactionLog receiverLog = TransactionLog.builder()
                .playerUuid(receiverUuid)
                .playerName(receiverName)
                .type(TransactionType.TRANSFER_IN)
                .amount(amount)
                .balanceBefore(receiverBalanceBefore)
                .balanceAfter(receiverBalanceAfter)
                .targetUuid(senderUuid)
                .targetName(senderName)
                .serverId(serverId)
                .adminAction(false)
                .timestamp(now)
                .build();

        return new TransactionLog[]{senderLog, receiverLog};
    }

    /**
     * Create admin action log
     */
    public static TransactionLog adminAction(UUID playerUuid, String playerName, TransactionType type,
                                             double amount, double balanceBefore, double balanceAfter,
                                             String adminName, String serverId, String reason) {
        return TransactionLog.builder()
                .playerUuid(playerUuid)
                .playerName(playerName)
                .type(type)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .serverId(serverId)
                .reason(reason)
                .adminAction(true)
                .adminName(adminName)
                .timestamp(Instant.now())
                .build();
    }
}