package com.settlement.reconciliation.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class AggregatedDisputePageResponse {
    private List<AggregatedDispute> items = new ArrayList<>();
    private long totalItems;
    private int totalPages;
    private int page;
    private int size;

    private long totalConflicts;
    private long pendingAction;
    private long pending;
    private long inInvestigation;
    private long resolutionSet;

    private List<String> availableTransactionTypes = new ArrayList<>();
    private List<String> availableBanks = new ArrayList<>();
    private List<String> availableDiscrepancyTypes = new ArrayList<>();
}

