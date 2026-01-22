package it.egeos.cut3g.airgap.service.files;

import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import it.egeos.cut3g.airgap.persistence.entity.TransactionEntity;
import it.egeos.cut3g.airgap.persistence.enums.Direction;
import it.egeos.cut3g.airgap.persistence.enums.TransactionState;
import it.egeos.cut3g.airgap.persistence.repo.PackageRepository;
import it.egeos.cut3g.airgap.persistence.repo.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TransactionService {
    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PackageRepository packageRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransactionEntity startTransaction(String packageId, Direction direction) {
        TransactionEntity tx = new TransactionEntity();
        PackageEntity pkg = packageRepository.getReferenceById(packageId);
        tx.setAirgapPackage(pkg);
        tx.setStartTs(Instant.now());
        tx.setState(TransactionState.STARTED);

        transactionRepository.save(tx);

        log.info("{} TX STARTED id={} packageId={}", direction, tx.getId(), packageId);

        return tx;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void closeSuccess(TransactionEntity tx, String note, Direction direction) {
        tx.setState(TransactionState.COMPLETED);
        tx.setEndTs(Instant.now());
        tx.setNote(note);
        transactionRepository.save(tx);

        log.info("{} TX COMPLETED id={} packageId={}",
                direction,
                tx.getId(),
                tx.getAirgapPackage() != null ? tx.getAirgapPackage().getId() : null
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void closeFailure(TransactionEntity tx, String note, Direction direction) {
        try {
            tx.setState(TransactionState.FAILED);
            tx.setEndTs(Instant.now());
            tx.setNote(note);
            transactionRepository.save(tx);

            log.warn("{} TX FAILED id={} packageId={}",
                    direction,
                    tx.getId(),
                    tx.getAirgapPackage() != null ? tx.getAirgapPackage().getId() : null
            );
        } catch (Exception e) {
            log.error("CRITICAL: Unable to persist FAILED transaction state for txId={}", tx.getId(), e);
        }
    }
}
