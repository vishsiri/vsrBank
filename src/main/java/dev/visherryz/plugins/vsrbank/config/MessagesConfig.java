package dev.visherryz.plugins.vsrbank.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Configuration
public class MessagesConfig {

    @Comment("Prefix for all messages (supports MiniMessage)")
    private String prefix = "<gradient:#FFD700:#FFA500>[VsrBank]</gradient> ";

    @Comment({"", "General Messages"})
    private String noPermission = "<red>You don't have permission to do this!</red>";
    private String playerOnly = "<red>This command can only be used by players!</red>";
    private String playerNotFound = "<red>Player <white>{player}</white> not found!</red>";
    private String invalidAmount = "<red>Please enter a valid amount!</red>";
    private String mustBePositive = "<red>Amount must be greater than 0!</red>";
    private String reloadSuccess = "<green>Configuration reloaded successfully!</green>";
    private String pluginDisabled = "<red>Bank system is currently disabled!</red>";

    @Comment({"", "Account Messages"})
    private String accountCreated = "<green>Your bank account has been created!</green>";
    private String noAccount = "<red>You don't have a bank account!</red>";
    private String targetNoAccount = "<red>{player} doesn't have a bank account!</red>";

    @Comment({"", "Balance Messages"})
    private String balanceDisplay = "<gold>Your bank balance: <white>{balance}</white></gold>";
    private String balanceOther = "<gold>{player}'s bank balance: <white>{balance}</white></gold>";
    private String maxBalanceReached = "<red>You've reached your maximum balance limit of <white>{max}</white>!</red>";
    private String walletEmpty = "<red>You don't have any money in your wallet!</red>";
    private String bankEmpty = "<red>Your bank account is empty!</red>";

    @Comment({"", "Deposit Messages"})
    private String depositSuccess = "<green>Successfully deposited <white>{amount}</white>! New balance: <white>{balance}</white></green>";
    private String depositClamped = "<yellow>Deposit adjusted to <white>{amount}</white> to fit your tier's max balance of <white>{max}</white>.</yellow>";
    private String depositFailed = "<red>Deposit failed! You don't have enough money.</red>";
    private String depositMinimum = "<red>Minimum deposit amount is <white>{min}</white>!</red>";
    private String depositWouldExceedMax = "<red>This deposit would exceed your maximum balance of <white>{max}</white>!</red>";

    @Comment({"", "Withdraw Messages"})
    private String withdrawSuccess = "<green>Successfully withdrew <white>{amount}</white>! New balance: <white>{balance}</white></green>";
    private String withdrawFailed = "<red>Withdrawal failed! Insufficient bank balance.</red>";
    private String withdrawMinimum = "<red>Minimum withdrawal amount is <white>{min}</white>!</red>";
    private String insufficientFunds = "<red>Insufficient funds! Your balance: <white>{balance}</white></red>";

    @Comment({"", "Transfer Messages"})
    private String transferSuccess = "<green>Successfully transferred <white>{amount}</white> to <white>{player}</white>!</green>";
    private String transferReceived = "<gold>You received <white>{amount}</white> from <white>{player}</white>!</gold>";
    private String transferFailed = "<red>Transfer failed! Please try again.</red>";
    private String transferMinimum = "<red>Minimum transfer amount is <white>{min}</white>!</red>";
    private String transferMaximum = "<red>Maximum transfer amount is <white>{max}</white>!</red>";
    private String transferToSelf = "<red>You cannot transfer money to yourself!</red>";
    private String transferOfflineDisabled = "<red>Transfers to offline players are disabled!</red>";
    private String transferFee = "<gray>Transfer fee: <white>{fee}</white></gray>";

    @Comment({"", "Interest Messages"})
    private String interestReceived = "<gold>You received <white>{amount}</white> interest!</gold>";
    private String interestNotEligible = "<gray>Minimum balance for interest: <white>{min}</white></gray>";

    @Comment({"", "Tier/Upgrade Messages"})
    private String currentTier = "<gold>Current tier: <white>{tier}</white> (Level {level})</gold>";
    private String tierBenefits = "<gray>Max Balance: <white>{maxBalance}</white> | Interest Rate: <white>{rate}x</white></gray>";
    private String upgradeSuccess = "<green>Bank upgraded to <white>{tier}</white>!</green>";
    private String upgradeFailed = "<red>Upgrade failed! Insufficient funds or XP.</red>";
    private String upgradeMaxTier = "<yellow>You've reached the maximum bank tier!</yellow>";
    private String upgradeRequirements = "<gray>Requirements: <white>{cost}</white> + <white>{xp} XP</white></gray>";
    private String upgradeNotEnoughMoney = "<red>You need <white>{cost}</white> to upgrade!</red>";
    private String upgradeNotEnoughXp = "<red>You need <white>{xp} XP</white> to upgrade!</red>";

    @Comment({"", "Transaction Lock Messages"})
    private String transactionLocked = "<red>Transaction in progress. Please wait...</red>";
    private String cooldownActive = "<red>Please wait <white>{seconds}s</white> before next transaction.</red>";

    @Comment({"", "Admin Messages"})
    private String adminGive = "<green>Gave <white>{amount}</white> to <white>{player}</white>'s bank.</green>";
    private String adminTake = "<green>Took <white>{amount}</white> from <white>{player}</white>'s bank.</green>";
    private String adminSet = "<green>Set <white>{player}</white>'s bank balance to <white>{amount}</white>.</green>";
    private String adminViewBalance = "<gold>{player}'s bank balance: <white>{balance}</white></gold>";
    private String adminViewTier = "<gold>{player}'s tier: <white>{tier}</white> (Level {level})</gold>";

    @Comment({"", "Database/Error Messages"})
    private String databaseError = "<red>A database error occurred. Please try again later.</red>";
    private String unexpectedError = "<red>An unexpected error occurred!</red>";
    private String redisError = "<red>Cross-server sync error. Transaction may be delayed.</red>";

    @Comment({"", "GUI Messages"})
    private String guiDepositTitle = "Enter deposit amount:";
    private String guiWithdrawTitle = "Enter withdrawal amount:";
    private String guiTransferTitle = "Enter transfer amount:";
    private String guiTransferPlayerTitle = "Enter player name:";
    private String guiInputCancelled = "<yellow>Input cancelled.</yellow>";
    private String guiInputTimeout = "<red>Input timed out!</red>";

    @Comment({"", "GUI Title Alerts (for chat input prompts)"})
    private String titleDepositMain = "<gold>Deposit</gold>";
    private String titleDepositSub = "<gray>Type amount in chat</gray>";
    private String titleWithdrawMain = "<gold>Withdraw</gold>";
    private String titleWithdrawSub = "<gray>Type amount in chat</gray>";
    private String titleTransferPlayerMain = "<gold>Transfer</gold>";
    private String titleTransferPlayerSub = "<gray>Type player name in chat</gray>";
    private String titleTransferAmountMain = "<gold>Transfer to {player}</gold>";
    private String titleTransferAmountSub = "<gray>Type amount in chat</gray>";

    @Comment({"", "History Messages"})
    private String historyEmpty = "<gray>No transaction history found.</gray>";
    private String historyEntry = "<gray>{date}</gray> <white>{type}</white>: <gold>{amount}</gold>";

    @Comment({"", "Help Messages"})
    private String helpHeader = "<gold>===== VsrBank Help =====</gold>";
    private String helpBalance = "<yellow>/bank</yellow> <gray>- View your bank balance</gray>";
    private String helpDeposit = "<yellow>/bank deposit <amount></yellow> <gray>- Deposit money</gray>";
    private String helpWithdraw = "<yellow>/bank withdraw <amount></yellow> <gray>- Withdraw money</gray>";
    private String helpTransfer = "<yellow>/bank transfer <player> <amount></yellow> <gray>- Transfer money</gray>";
    private String helpUpgrade = "<yellow>/bank upgrade</yellow> <gray>- Upgrade your bank tier</gray>";
    private String helpHistory = "<yellow>/bank history</yellow> <gray>- View transaction history</gray>";
}