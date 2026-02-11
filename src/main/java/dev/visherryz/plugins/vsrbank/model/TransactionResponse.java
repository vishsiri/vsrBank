package dev.visherryz.plugins.vsrbank.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper class for transaction results
 * Contains both the result status and the new balance
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {

    /**
     * The result of the operation
     */
    private BankResult result;

    /**
     * New balance after the operation (if successful)
     */
    private double newBalance;

    /**
     * Previous balance before the operation
     */
    private double previousBalance;

    /**
     * Amount that was actually processed
     */
    private double processedAmount;

    /**
     * Any fee that was applied
     */
    private double fee;

    /**
     * Additional message for the result
     */
    private String message;

    /**
     * Failed requirements (for PlaceholderAPI tier requirements) - NEW
     */
    @Builder.Default
    private List<String> failedRequirements = new ArrayList<>();

    /**
     * Create a success response
     */
    public static TransactionResponse success(double previousBalance, double newBalance, double processedAmount) {
        return TransactionResponse.builder()
                .result(BankResult.SUCCESS)
                .previousBalance(previousBalance)
                .newBalance(newBalance)
                .processedAmount(processedAmount)
                .fee(0)
                .build();
    }

    /**
     * Create a success response with fee
     */
    public static TransactionResponse successWithFee(double previousBalance, double newBalance,
                                                     double processedAmount, double fee) {
        return TransactionResponse.builder()
                .result(BankResult.SUCCESS)
                .previousBalance(previousBalance)
                .newBalance(newBalance)
                .processedAmount(processedAmount)
                .fee(fee)
                .build();
    }

    /**
     * Create a failure response
     */
    public static TransactionResponse failure(BankResult result) {
        return TransactionResponse.builder()
                .result(result)
                .build();
    }

    /**
     * Create a failure response with message
     */
    public static TransactionResponse failure(BankResult result, String message) {
        return TransactionResponse.builder()
                .result(result)
                .message(message)
                .build();
    }

    /**
     * Create a failure response with current balance info
     */
    public static TransactionResponse failure(BankResult result, double currentBalance) {
        return TransactionResponse.builder()
                .result(result)
                .previousBalance(currentBalance)
                .newBalance(currentBalance)
                .build();
    }

    /**
     * Create a failure response with failed requirements (NEW)
     * For PlaceholderAPI tier upgrade requirements
     */
    public static TransactionResponse failureWithRequirements(BankResult result, List<String> failedRequirements) {
        return TransactionResponse.builder()
                .result(result)
                .failedRequirements(failedRequirements != null ? failedRequirements : new ArrayList<>())
                .build();
    }

    /**
     * Check if the transaction was successful
     */
    public boolean isSuccess() {
        return result != null && result.isSuccess();
    }

    /**
     * Check if the transaction failed
     */
    public boolean isFailure() {
        return result == null || result.isFailure();
    }

    /**
     * Check if this response has failed requirements (NEW)
     */
    public boolean hasFailedRequirements() {
        return failedRequirements != null && !failedRequirements.isEmpty();
    }
}