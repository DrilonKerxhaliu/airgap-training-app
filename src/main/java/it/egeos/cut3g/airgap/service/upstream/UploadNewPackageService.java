package it.egeos.cut3g.airgap.service.upstream;

import it.egeos.cut3g.airgap.persistence.entity.UploadPackageEntity;
import it.egeos.cut3g.airgap.persistence.repo.UploadPackageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UploadNewPackageService {

    @Autowired
    private UploadPackageRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UploadPackageEntity saveNewPackage(UploadPackageEntity pkg) {

        UploadPackageEntity entity;

        if (pkg.getId() == null || !repository.existsById(pkg.getId())) {
            entity = repository.save(pkg);
        } else {
            entity = repository.findById(pkg.getId())
                    .orElseThrow(() -> new RuntimeException("UploadPackage not found"));

            entity.setStatus(pkg.getStatus());
            entity.setNote(pkg.getNote());
            entity.setWorkDirPath(pkg.getWorkDirPath());
            entity.setOuterDirPath(pkg.getOuterDirPath());
            entity.setDataDirPath(pkg.getDataDirPath());
            entity.setManifestRelativePath(pkg.getManifestRelativePath());
            entity.setManifestMd5DataTar(pkg.getManifestMd5DataTar());
            entity.setFileCount(pkg.getFileCount());
            entity.setTotalSizeBytes(pkg.getTotalSizeBytes());
            entity.setArchivedTarPath(pkg.getArchivedTarPath());
            entity.setArchivedAt(pkg.getArchivedAt());
            entity.setImportedAt(pkg.getImportedAt());
        }

        repository.flush();
        return entity;
    }
}