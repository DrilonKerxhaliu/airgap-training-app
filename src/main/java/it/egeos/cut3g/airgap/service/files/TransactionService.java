package it.egeos.cut3g.airgap.service.files;

import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import it.egeos.cut3g.airgap.persistence.entity.TransactionEntity;
import it.egeos.cut3g.airgap.persistence.entity.UploadPackageEntity;
import it.egeos.cut3g.airgap.persistence.enums.Direction;
import it.egeos.cut3g.airgap.persistence.enums.PackageState;
import it.egeos.cut3g.airgap.persistence.enums.TransactionState;
import it.egeos.cut3g.airgap.persistence.enums.UploadPackageStatus;
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
    public TransactionEntity startUploadTransaction(UploadPackageEntity uploadPkg, Direction direction, String username) {

        TransactionEntity tx = new TransactionEntity();
        tx.setUploadPackage(uploadPkg);
        tx.setUploadPackageStatus(uploadPkg.getStatus());
        tx.setDirection(direction);
        tx.setStartTs(Instant.now());
        tx.setState(TransactionState.STARTED);
        tx.setInitiatedBy(username != null ? username : "auto");
        tx.setNote("Upload package=" + uploadPkg.getPackageName());

        transactionRepository.save(tx);

        log.info("{} TX STARTED id={} uploadPackageId={}", direction, tx.getId(), uploadPkg.getId());
        return tx;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void closeSuccess(String txId, String note) {
        TransactionEntity tx = transactionRepository.findById(txId)
                .orElseThrow(() -> new IllegalArgumentException("TX not found: " + txId));

        tx.setState(TransactionState.COMPLETED);
        tx.setEndTs(Instant.now());
        tx.setNote(note);

        if (tx.isDownstream()) {
            if (tx.getAirgapPackage() != null) {
                tx.setPackageState(tx.getAirgapPackage().getState());
            }
        } else if (tx.isUpstream()) {
            if (tx.getUploadPackage() != null) {
                tx.setUploadPackageStatus(tx.getUploadPackage().getStatus());
            }
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

        if (tx.isDownstream()) {
            if (tx.getAirgapPackage() != null) {
                tx.setPackageState(tx.getAirgapPackage().getState());
            } else {
                tx.setPackageState(PackageState.FAILED);
            }
        } else if (tx.isUpstream()) {
            if (tx.getUploadPackage() != null) {
                tx.setUploadPackageStatus(tx.getUploadPackage().getStatus());
            } else {
                tx.setUploadPackageStatus(UploadPackageStatus.FAILED);
            }
        }

        transactionRepository.save(tx);

        log.warn("{} TX FAILED id={} note={}", tx.getDirection(), tx.getId(), note);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransactionEntity noPackageTransaction(Direction direction, String note, String username) {

        TransactionEntity tx = new TransactionEntity();
        tx.setDirection(direction);
        tx.setStartTs(Instant.now());
        tx.setState(TransactionState.FAILED);
        tx.setInitiatedBy(username != null ? username : "auto");
        tx.setNote(note);

        transactionRepository.save(tx);

        log.info("{} TX STARTED id={} no package created !", direction, tx.getId());
        return tx;
    }
}