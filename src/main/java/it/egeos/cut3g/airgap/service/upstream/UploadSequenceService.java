package it.egeos.cut3g.airgap.service.upstream;

import it.egeos.cut3g.airgap.exceptions.SequenceMismatchException;
import it.egeos.cut3g.airgap.persistence.entity.UploadSequenceEntity;
import it.egeos.cut3g.airgap.persistence.repo.UploadSequenceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class UploadSequenceService {

    @Autowired
    private UploadSequenceRepository repository;

    @Transactional
    public long assertExpectedNext(long incomingSequence) {
        UploadSequenceEntity seq = repository.findByIdForUpdate(1L)
                .orElseThrow(() -> new IllegalStateException("Upload sequence row id=1 not found"));

        long expected = seq.getLastSequenceIndex() + 1;

        if (incomingSequence != expected) {
            throw new SequenceMismatchException(
                    "Package rejected. Expected sequence "
                            + String.format("%06d", expected)
                            + " but received "
                            + String.format("%06d", incomingSequence)
            );
        }

        return expected;
    }

    @Transactional
    public void markProcessed(long sequence) {
        UploadSequenceEntity seq = repository.findByIdForUpdate(1L)
                .orElseThrow(() -> new IllegalStateException("Upload sequence row id=1 not found"));

        long expected = seq.getLastSequenceIndex() + 1;
        if (sequence != expected) {
            throw new IllegalStateException("Cannot mark processed. Expected next="
                    + expected + " but got " + sequence);
        }

        seq.setLastSequenceIndex(sequence);
        seq.setUpdatedAt(Instant.now());
        repository.save(seq);
    }
}