package br.unicamp.ft.si600.eventos.dto;

import java.math.BigDecimal;

public record DashboardFinancialSummaryResponse(
        long totalProjectFees,
        long pendingQuotesCount,
        long awaitingQuantityCount,
        long quotedFeesCount,
        long generatedPaymentsCount,
        BigDecimal estimatedRevenue
) {
    public static DashboardFinancialSummaryResponse empty() {
        return new DashboardFinancialSummaryResponse(0L, 0L, 0L, 0L, 0L, BigDecimal.ZERO.setScale(2));
    }
}
