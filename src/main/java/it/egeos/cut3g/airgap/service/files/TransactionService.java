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
        tx.setPackageState(pkg.getState());
        tx.setState(TransactionState.STARTED);

        transactionRepository.save(tx);

        log.info("{} TX STARTED id={} packageId={}", direction, tx.getId(), packageId);

        return tx;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void closeSuccess(TransactionEntity tx, String note, Direction direction) {
        PackageEntity pkg = tx.getAirgapPackage();
        tx.setState(TransactionState.COMPLETED);
        tx.setEndTs(Instant.now());
        tx.setNote(note);
        if (pkg != null) {
        tx.setPackageState(pkg.getState());
    }
        transactionRepository.save(tx);

        log.info("{} TX COMPLETED id={} packageId={} packageState={}",
                direction,
                tx.getId(),
                pkg != null ? pkg.getId() : null,
                tx.getPackageState()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void closeFailure(TransactionEntity tx, String note, Direction direction) {
        try {
            PackageEntity pkg = tx.getAirgapPackage();
            tx.setState(TransactionState.FAILED);
            tx.setEndTs(Instant.now());
            tx.setNote(note);

            if (pkg != null) {
            tx.setPackageState(pkg.getState());
        }
            transactionRepository.save(tx);

            log.warn("{} TX FAILED id={} packageId={} packageState={}",
                    direction,
                    tx.getId(),
                    pkg != null ? pkg.getId() : null,
                    tx.getPackageState()
            );
        } catch (Exception e) {
            log.error("CRITICAL: Unable to persist FAILED transaction state for txId={}", tx.getId(), e);
        }
    }
}
