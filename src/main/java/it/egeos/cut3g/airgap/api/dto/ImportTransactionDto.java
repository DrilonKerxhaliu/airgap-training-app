package it.egeos.cut3g.airgap.api.dto;

import it.egeos.cut3g.airgap.persistence.entity.TransactionEntity;
import it.egeos.cut3g.airgap.persistence.enums.Direction;
import it.egeos.cut3g.airgap.persistence.enums.TransactionState;

import java.time.Instant;

public class ImportTransactionDto {
    public String transactionId;
    public TransactionState state;
    public String note;
    public Instant startTs;
    public Instant endTs;
    public Direction direction;
    public String packageId;

    public static ImportTransactionDto from(TransactionEntity tx, Direction direction) {
        ImportTransactionDto dto = new ImportTransactionDto();
        dto.transactionId = tx.getId();
        dto.state = tx.getState();
        dto.note = tx.getNote();
        dto.startTs = tx.getStartTs();
        dto.endTs = tx.getEndTs();
        dto.direction = direction;
        dto.packageId = tx.getAirgapPackage() != null
                ? tx.getAirgapPackage().getId()
                : null;
        return dto;
    }
}
