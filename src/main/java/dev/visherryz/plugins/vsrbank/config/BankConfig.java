package dev.visherryz.plugins.vsrbank.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Configuration
public class BankConfig {

    @Comment("Server identifier for cross-server communication")
    private String serverId = "server-1";

    @Comment({"", "Database Configuration"})
    private DatabaseSettings database = new DatabaseSettings();

    @Comment({"", "Redis Configuration for Cross-Server Sync"})
    private RedisSettings redis = new RedisSettings();

    @Comment({"", "Interest System Configuration"})
    private InterestSettings interest = new InterestSettings();

    @Comment({"", "Bank Tier Configuration"})
    private Map<Integer, TierSettings> tiers = createDefaultTiers();

    @Comment({"", "Transaction Settings"})
    private TransactionSettings transaction = new TransactionSettings();

    @Comment({"", "Discord Webhook Settings"})
    private DiscordSettings discord = new DiscordSettings();

    @Comment({"", "GUI Settings"})
    private GuiSettings gui = new GuiSettings();

    @Comment({"", "Economy Settings"})
    private EconomySettings economy = new EconomySettings();

    @Getter
    @Setter
    @Configuration
    public static class DatabaseSettings {
        @Comment("Database type: MYSQL, MARIADB, or SQLITE")
        private String type = "SQLITE";

        @Comment("MySQL/MariaDB host")
        private String host = "localhost";

        @Comment("MySQL/MariaDB port")
        private int port = 3306;

        @Comment("Database name")
        private String database = "minecraft_bank";

        @Comment("Database username")
        private String username = "root";

        @Comment("Database password")
        private String password = "password";

        @Comment("Connection pool settings")
        private int maxPoolSize = 10;
        private int minIdle = 5;
        private long connectionTimeout = 30000;
        private long idleTimeout = 600000;
        private long maxLifetime = 1800000;

        @Comment("Table prefix")
        private String tablePrefix = "vsrbank_";
    }

    @Getter
    @Setter
    @Configuration
    public static class RedisSettings {
        @Comment("Enable Redis for cross-server sync")
        private boolean enabled = false;

        // ===== Cluster Identifier =====
        @Comment({
                "Cluster identifier for Redis pub/sub topics",
                "Servers with the same cluster ID will share balance updates",
                "Examples: 'survival', 'creative', 'skyblock'",
                "Default: uses server-id (recommended for most setups)"
        })
        private String clusterId = "";

        // ===== Standalone Settings =====
        @Comment("Redis host (standalone mode)")
        private String host = "localhost";

        @Comment("Redis port (standalone mode)")
        private int port = 6379;

        @Comment("Redis password (leave empty if none)")
        private String password = "";

        @Comment("Redis database index (standalone mode only)")
        private int database = 0;

        // ===== Cluster Settings =====
        @Comment("Enable Redis Cluster mode")
        private boolean clusterMode = false;

        @Comment({
                "Cluster nodes (only used if clusterMode is true)",
                "Format: host:port",
                "Example: node1:7000, node2:7001, node3:7002"
        })
        private List<String> clusterNodes = List.of(
                "localhost:7000",
                "localhost:7001",
                "localhost:7002",
                "localhost:7003",
                "localhost:7004",
                "localhost:7005"
        );

        // ===== Connection Pool Settings =====
        @Comment("Connection pool size (recommended: 64 for production)")
        private int connectionPoolSize = 64;

        @Comment("Minimum idle connections (recommended: 10)")
        private int connectionMinimumIdleSize = 10;

        @Comment("Connection timeout in milliseconds")
        private int timeout = 3000;

        // ===== Lock Settings =====
        @Comment("Lock timeout in milliseconds")
        private long lockTimeout = 5000;
    }

    @Getter
    @Setter
    @Configuration
    public static class InterestSettings {
        @Comment("Enable interest system")
        private boolean enabled = true;

        @Comment("Interest calculation interval in minutes")
        private int intervalMinutes = 60;

        @Comment("Base interest rate (0.01 = 1%)")
        private double baseRate = 0.01;

        @Comment("Interest rate for offline players (0.005 = 0.5%)")
        private double offlineRate = 0.005;

        @Comment("Minimum balance to earn interest")
        private double minBalanceForInterest = 100.0;

        @Comment("Maximum interest per cycle")
        private double maxInterestPerCycle = 10000.0;
    }

    @Getter
    @Setter
    @Configuration
    public static class TierSettings {
        private String name = "Default";
        private double maxBalance = 10000.0;
        private double interestMultiplier = 1.0;
        private double upgradeCost = 0.0;
        private int upgradeXpCost = 0;
        private String displayItem = "GOLD_INGOT";
        private int customModelData = 0;
        private List<String> lore = List.of("&7Max Balance: &f{max_balance}", "&7Interest Rate: &f{rate}x");

        public TierSettings() {}

        public TierSettings(String name, double maxBalance, double interestMultiplier,
                            double upgradeCost, int upgradeXpCost, String displayItem) {
            this.name = name;
            this.maxBalance = maxBalance;
            this.interestMultiplier = interestMultiplier;
            this.upgradeCost = upgradeCost;
            this.upgradeXpCost = upgradeXpCost;
            this.displayItem = displayItem;
        }
    }

    @Getter
    @Setter
    @Configuration
    public static class TransactionSettings {
        @Comment("Cooldown between transactions in milliseconds")
        private long cooldownMs = 500;

        @Comment("Minimum transfer amount")
        private double minTransferAmount = 1.0;

        @Comment("Maximum transfer amount (0 = unlimited)")
        private double maxTransferAmount = 0.0;

        @Comment("Transfer fee percentage (0.01 = 1%)")
        private double transferFeePercent = 0.0;

        @Comment("Minimum deposit amount")
        private double minDepositAmount = 1.0;

        @Comment("Minimum withdrawal amount")
        private double minWithdrawAmount = 1.0;

        @Comment("Allow transfers to offline players")
        private boolean allowOfflineTransfer = true;
    }

    @Getter
    @Setter
    @Configuration
    public static class DiscordSettings {
        @Comment("Enable Discord webhook notifications")
        private boolean enabled = false;

        @Comment("Webhook URL for admin notifications")
        private String adminWebhookUrl = "";

        @Comment("Webhook URL for suspicious activity alerts")
        private String alertWebhookUrl = "";

        @Comment("Notify on admin commands")
        private boolean notifyAdminCommands = true;

        @Comment("Suspicious activity threshold (transfers above this amount)")
        private double suspiciousThreshold = 100000.0;
    }

    @Getter
    @Setter
    @Configuration
    public static class EconomySettings {
        @Comment("Register as Vault economy provider")
        private boolean registerAsVaultProvider = false;

        @Comment("Currency name singular")
        private String currencyNameSingular = "Dollar";

        @Comment("Currency name plural")
        private String currencyNamePlural = "Dollars";

        @Comment("Currency symbol")
        private String currencySymbol = "$";

        @Comment("Format: SYMBOL_FIRST or SYMBOL_LAST")
        private String currencyFormat = "SYMBOL_FIRST";

        @Comment("Decimal places for display")
        private int decimalPlaces = 2;
    }

    private Map<Integer, TierSettings> createDefaultTiers() {
        Map<Integer, TierSettings> defaultTiers = new LinkedHashMap<>();
        defaultTiers.put(1, new TierSettings("Bronze", 10000.0, 1.0, 0.0, 0, "COPPER_INGOT"));
        defaultTiers.put(2, new TierSettings("Silver", 50000.0, 1.25, 5000.0, 100, "IRON_INGOT"));
        defaultTiers.put(3, new TierSettings("Gold", 200000.0, 1.5, 25000.0, 500, "GOLD_INGOT"));
        defaultTiers.put(4, new TierSettings("Diamond", 1000000.0, 2.0, 100000.0, 1000, "DIAMOND"));
        defaultTiers.put(5, new TierSettings("Netherite", -1.0, 3.0, 500000.0, 5000, "NETHERITE_INGOT"));
        return defaultTiers;
    }

    /**
     * Get tier settings by level
     */
    public TierSettings getTier(int level) {
        return tiers.getOrDefault(level, tiers.get(1));
    }

    /**
     * Get maximum tier level
     */
    public int getMaxTier() {
        return tiers.keySet().stream().mapToInt(Integer::intValue).max().orElse(1);
    }
}