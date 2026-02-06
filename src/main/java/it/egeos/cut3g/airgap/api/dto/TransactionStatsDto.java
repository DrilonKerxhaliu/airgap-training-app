package it.egeos.cut3g.airgap.api.dto;

import it.egeos.cut3g.airgap.persistence.enums.PackageState;
import it.egeos.cut3g.airgap.persistence.enums.TransactionState;

public class TransactionStatsDto {

    private TransactionState transactionState;
    private PackageState packageState;
    private long count;

    public TransactionStatsDto(
            TransactionState transactionState,
            PackageState packageState,
            long count
    ) {
        this.transactionState = transactionState;
        this.packageState = packageState;
        this.count = count;
    }

    public TransactionState getTransactionState() { return transactionState; }
    public PackageState getPackageState() { return packageState; }
    public long getCount() { return count; }
}
