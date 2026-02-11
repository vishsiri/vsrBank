package dev.visherryz.plugins.vsrbank.service;

import dev.visherryz.plugins.vsrbank.VsrBank;
import dev.visherryz.plugins.vsrbank.config.BankConfig;
import lombok.RequiredArgsConstructor;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for checking tier upgrade requirements using PlaceholderAPI
 */
@RequiredArgsConstructor
public class TierRequirementService {

    private final VsrBank plugin;

    // Patterns for parsing requirements
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%([^%]+)%");
    private static final Pattern COMPARISON_PATTERN = Pattern.compile("(.+?)\\s*(>=|<=|>|<|==|!=)\\s*(.+)");
    private static final Pattern PERMISSION_PATTERN = Pattern.compile("permission:(.+)");

    /**
     * Check if player meets all requirements for a tier
     *
     * @param player The player to check
     * @param tier The tier settings to check against
     * @return Result containing success status and list of failed requirements
     */
    public RequirementCheckResult checkRequirements(Player player, BankConfig.TierSettings tier) {
        List<String> requirements = tier.getRequirements();

        if (requirements == null || requirements.isEmpty()) {
            return RequirementCheckResult.success();
        }

        List<String> failedRequirements = new ArrayList<>();

        for (String requirement : requirements) {
            requirement = requirement.trim();

            // Skip empty lines or comments
            if (requirement.isEmpty() || requirement.startsWith("#")) {
                continue;
            }

            if (!checkSingleRequirement(player, requirement)) {
                failedRequirements.add(requirement);
            }
        }

        if (failedRequirements.isEmpty()) {
            return RequirementCheckResult.success();
        } else {
            return RequirementCheckResult.failure(failedRequirements);
        }
    }

    /**
     * Check a single requirement string
     */
    private boolean checkSingleRequirement(Player player, String requirement) {
        // Check if it's a permission requirement
        Matcher permMatcher = PERMISSION_PATTERN.matcher(requirement);
        if (permMatcher.matches()) {
            String permission = permMatcher.group(1);
            return player.hasPermission(permission);
        }

        // Parse comparison requirement (e.g., "%player_level% >= 20")
        Matcher compMatcher = COMPARISON_PATTERN.matcher(requirement);
        if (compMatcher.matches()) {
            String leftSide = compMatcher.group(1).trim();
            String operator = compMatcher.group(2).trim();
            String rightSide = compMatcher.group(3).trim();

            // Replace placeholders
            leftSide = replacePlaceholders(player, leftSide);
            rightSide = replacePlaceholders(player, rightSide);

            return compareValues(leftSide, operator, rightSide);
        }

        // If no pattern matches, log warning and return true to not block
        plugin.getLogger().warning("Invalid requirement format: " + requirement);
        return true;
    }

    /**
     * Replace PlaceholderAPI placeholders in a string
     */
    private String replacePlaceholders(Player player, String text) {
        if (!plugin.hasPlaceholderAPI()) {
            return text;
        }

        return PlaceholderAPI.setPlaceholders(player, text);
    }

    /**
     * Compare two values using the given operator
     * Tries to parse as numbers first, falls back to string comparison
     */
    private boolean compareValues(String left, String operator, String right) {
        // Try numeric comparison first
        try {
            double leftNum = Double.parseDouble(left);
            double rightNum = Double.parseDouble(right);

            return switch (operator) {
                case ">=" -> leftNum >= rightNum;
                case "<=" -> leftNum <= rightNum;
                case ">" -> leftNum > rightNum;
                case "<" -> leftNum < rightNum;
                case "==" -> Math.abs(leftNum - rightNum) < 0.0001; // Float equality
                case "!=" -> Math.abs(leftNum - rightNum) >= 0.0001;
                default -> false;
            };
        } catch (NumberFormatException e) {
            // Fall back to string comparison
            return switch (operator) {
                case "==" -> left.equalsIgnoreCase(right);
                case "!=" -> !left.equalsIgnoreCase(right);
                default -> false; // >, <, >=, <= don't make sense for strings
            };
        }
    }

    /**
     * Get human-readable description of a requirement
     * Useful for displaying to players what they're missing
     */
    public String getRequirementDescription(Player player, String requirement) {
        requirement = requirement.trim();

        // Permission requirement
        Matcher permMatcher = PERMISSION_PATTERN.matcher(requirement);
        if (permMatcher.matches()) {
            String permission = permMatcher.group(1);
            boolean has = player.hasPermission(permission);
            return String.format("Permission: %s %s", permission, has ? "✓" : "✗");
        }

        // Comparison requirement
        Matcher compMatcher = COMPARISON_PATTERN.matcher(requirement);
        if (compMatcher.matches()) {
            String leftSide = compMatcher.group(1).trim();
            String operator = compMatcher.group(2).trim();
            String rightSide = compMatcher.group(3).trim();

            String leftValue = replacePlaceholders(player, leftSide);
            String rightValue = replacePlaceholders(player, rightSide);

            boolean met = compareValues(leftValue, operator, rightValue);

            // Make it human readable
            String displayLeft = leftSide.contains("%") ? leftValue : leftSide;
            String displayRight = rightSide.contains("%") ? rightValue : rightSide;

            return String.format("%s %s %s %s", displayLeft, operator, displayRight, met ? "✓" : "✗");
        }

        return requirement;
    }

    /**
     * Result of a requirement check
     */
    public static class RequirementCheckResult {
        private final boolean success;
        private final List<String> failedRequirements;

        private RequirementCheckResult(boolean success, List<String> failedRequirements) {
            this.success = success;
            this.failedRequirements = failedRequirements != null ? failedRequirements : new ArrayList<>();
        }

        public static RequirementCheckResult success() {
            return new RequirementCheckResult(true, null);
        }

        public static RequirementCheckResult failure(List<String> failedRequirements) {
            return new RequirementCheckResult(false, failedRequirements);
        }

        public boolean isSuccess() {
            return success;
        }

        public List<String> getFailedRequirements() {
            return failedRequirements;
        }

        public boolean hasFailed() {
            return !success;
        }
    }
}