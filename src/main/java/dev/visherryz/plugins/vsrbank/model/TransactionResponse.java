package dev.visherryz.plugins.vsrbank.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}