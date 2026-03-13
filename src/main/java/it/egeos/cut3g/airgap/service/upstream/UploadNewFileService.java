package it.egeos.cut3g.airgap.service.upstream;

import it.egeos.cut3g.airgap.persistence.entity.UploadFileEntity;
import it.egeos.cut3g.airgap.persistence.repo.UploadFileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UploadNewFileService {

    @Autowired
    private UploadFileRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UploadFileEntity saveNewFile(UploadFileEntity file) {

        UploadFileEntity entity;

        if (file.getId() == null || !repository.existsById(file.getId())) {
            entity = repository.save(file);
        } else {

            entity = repository.findById(file.getId())
                    .orElseThrow(() -> new RuntimeException("UploadFile not found: " + file.getId()));

            entity.setRelativePath(file.getRelativePath());
            entity.setExtractedAbsolutePath(file.getExtractedAbsolutePath());
            entity.setDeliveredAbsolutePath(file.getDeliveredAbsolutePath());
            entity.setSizeBytes(file.getSizeBytes());
            entity.setChecksumMd5(file.getChecksumMd5());
            entity.setStatus(file.getStatus());
            entity.setNote(file.getNote());
        }

        repository.flush();
        return entity;
    }
}