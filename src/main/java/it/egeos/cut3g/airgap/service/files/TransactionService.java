package it.egeos.cut3g.airgap.service.files;

import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import it.egeos.cut3g.airgap.persistence.entity.TransactionEntity;
import it.egeos.cut3g.airgap.persistence.enums.Direction;
import it.egeos.cut3g.airgap.persistence.enums.PackageState;
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
import java.util.Optional;

@Service
public class TransactionService {
    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PackageRepository packageRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransactionEntity startTransaction(String packageId, Direction direction, String username) {
        TransactionEntity tx = new TransactionEntity();
        Optional<PackageEntity> pkgOptional = packageRepository.findById(packageId);
        if(!pkgOptional.isEmpty()) {
           PackageEntity pkg = pkgOptional.get();
            tx.setAirgapPackage(pkg);
            tx.setStartTs(Instant.now());
            tx.setPackageState(pkg.getState());
            tx.setState(TransactionState.STARTED);
            tx.setInitiatedBy(username != null ? username : "auto");

        }else{
            closeFailure(tx.getId(), "Package not found: " + packageId, direction, username);
        }
        transactionRepository.save(tx);

        log.info("{} TX STARTED id={} packageId={}", direction, tx.getId(), packageId);

        return tx;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void closeSuccess(String txId, String note, Direction direction) {
        TransactionEntity tx = transactionRepository.getReferenceById(txId);
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
    public void closeFailure(String txId, String note, Direction direction, String username) {

        Optional<TransactionEntity> txById = transactionRepository.findById(txId);
        if(!txById.isEmpty()) {
            TransactionEntity tx = txById.get();
            PackageEntity pkg = tx.getAirgapPackage();
            tx.setState(TransactionState.FAILED);
            tx.setEndTs(Instant.now());
            tx.setNote(note);
            tx.setInitiatedBy(username != null ? username : "auto");

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
        }else{
            TransactionEntity tx = new TransactionEntity();
            tx.setState(TransactionState.FAILED);
            tx.setStartTs(Instant.now());
            tx.setEndTs(Instant.now());
            tx.setNote(note);
            tx.setPackageState(PackageState.FAILED);
            tx.setInitiatedBy(username != null ? username : "auto");

            transactionRepository.save(tx);

            log.warn("{} TX with id={} not completed due to process failure.",
                    direction,
                    tx.getId()
            );
        }
    }
}
