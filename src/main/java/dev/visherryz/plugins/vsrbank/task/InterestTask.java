package dev.visherryz.plugins.vsrbank.task;

import dev.visherryz.plugins.vsrbank.VsrBank;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Scheduled task for calculating and applying interest
 */
public class InterestTask extends BukkitRunnable {

    private final VsrBank plugin;

    public InterestTask(VsrBank plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.getConfigManager().getConfig().getInterest().isEnabled()) {
            return;
        }

        plugin.getLogger().info("Starting interest calculation...");

        plugin.getInterestService().processInterest()
                .thenAccept(count -> {
                    plugin.getLogger().info("Interest applied to " + count + " accounts");
                })
                .exceptionally(ex -> {
                    plugin.getLogger().warning("Failed to process interest: " + ex.getMessage());
                    return null;
                });
    }

    /**
     * Start the interest task with configured interval
     */
    public void start() {
        int intervalMinutes = plugin.getConfigManager().getConfig().getInterest().getIntervalMinutes();
        long intervalTicks = intervalMinutes * 60L * 20L; // Convert to ticks

        // Run async, first run after interval, then repeat
        this.runTaskTimerAsynchronously(plugin, intervalTicks, intervalTicks);

        plugin.getLogger().info("Interest task scheduled every " + intervalMinutes + " minutes");
    }
}
