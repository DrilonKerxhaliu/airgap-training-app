package it.egeos.cut3g.airgap.service.files;

import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import it.egeos.cut3g.airgap.persistence.entity.TransactionEntity;
import it.egeos.cut3g.airgap.persistence.entity.UploadPackageEntity;
import it.egeos.cut3g.airgap.persistence.enums.Direction;
import it.egeos.cut3g.airgap.persistence.enums.PackageState;
import it.egeos.cut3g.airgap.persistence.enums.TransactionState;
import it.egeos.cut3g.airgap.persistence.repo.PackageRepository;
import it.egeos.cut3g.airgap.persistence.repo.TransactionRepository;
import it.egeos.cut3g.airgap.persistence.repo.UploadPackageRepository;
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

    @Autowired
    private UploadPackageRepository uploadPackageRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransactionEntity startTransaction(String packageId, Direction direction, String username) {
        PackageEntity pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Package not found: " + packageId));

        TransactionEntity tx = new TransactionEntity();
        tx.setAirgapPackage(pkg);
        tx.setDirection(direction);
        tx.setStartTs(Instant.now());
        tx.setPackageState(pkg.getState());
        tx.setState(TransactionState.STARTED);
        tx.setInitiatedBy(username != null ? username : "auto");

        transactionRepository.save(tx);

        log.info("{} TX STARTED id={} packageId={}", direction, tx.getId(), packageId);
        return tx;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransactionEntity startUploadTransaction(String uploadPackageId, Direction direction, String username) {
        UploadPackageEntity uploadPkg = uploadPackageRepository.findById(uploadPackageId)
                .orElseThrow(() -> new IllegalArgumentException("Upload package not found: " + uploadPackageId));

        TransactionEntity tx = new TransactionEntity();
        tx.setUploadPackage(uploadPkg);
        tx.setDirection(direction);
        tx.setStartTs(Instant.now());
        tx.setState(TransactionState.STARTED);
        tx.setInitiatedBy(username != null ? username : "auto");
        tx.setNote("Upload package=" + uploadPkg.getPackageName());

        transactionRepository.save(tx);

        log.info("{} TX STARTED id={} uploadPackageId={}", direction, tx.getId(), uploadPackageId);
        return tx;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void closeSuccess(String txId, String note) {
        TransactionEntity tx = transactionRepository.findById(txId)
                .orElseThrow(() -> new IllegalArgumentException("TX not found: " + txId));

        tx.setState(TransactionState.COMPLETED);
        tx.setEndTs(Instant.now());
        tx.setNote(note);

        if (tx.getAirgapPackage() != null) {
            tx.setPackageState(tx.getAirgapPackage().getState());
        }

        transactionRepository.save(tx);

        log.info("{} TX COMPLETED id={}", tx.getDirection(), tx.getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void closeFailure(String txId, String note, String username) {
        TransactionEntity tx = transactionRepository.findById(txId)
                .orElseThrow(() -> new IllegalArgumentException("TX not found: " + txId));

        tx.setState(TransactionState.FAILED);
        tx.setEndTs(Instant.now());
        tx.setNote(note);
        tx.setInitiatedBy(username != null ? username : tx.getInitiatedBy());

        if (tx.getAirgapPackage() != null) {
            tx.setPackageState(tx.getAirgapPackage().getState());
        } else {
            tx.setPackageState(PackageState.FAILED);
        }

        transactionRepository.save(tx);

        log.warn("{} TX FAILED id={} note={}", tx.getDirection(), tx.getId(), note);
    }
}