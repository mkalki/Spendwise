package com.spendwise.checkout.dto;

public record TransactionSummaryResponse(
        String weekStart,
        String weekEnd,
        String completedSpend,
        String activeReservations,
        String remainingBudget,
        long completedOrders,
        long activeReservationCount,
        int maxOrdersPerWeek,
        String currency) {
}
