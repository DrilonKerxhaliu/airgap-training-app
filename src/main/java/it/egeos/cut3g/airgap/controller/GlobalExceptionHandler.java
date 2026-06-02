package it.egeos.cut3g.airgap.controller;

import it.egeos.cut3g.airgap.api.ApiResponse;
import it.egeos.cut3g.airgap.exceptions.*;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PackageNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handlePackageNotFound(PackageNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(PackageFileNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handlePackageFileNotFound(PackageFileNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(NoNewFilesToPackageException.class)
    public ResponseEntity<ApiResponse<?>> handleNoNewFiles(NoNewFilesToPackageException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(DownstreamConflictException.class)
    public ResponseEntity<ApiResponse<?>> handleConflict(DownstreamConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(DownstreamIOException.class)
    public ResponseEntity<ApiResponse<?>> handleIO(DownstreamIOException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGeneric(Exception ex) {
        log.error("Unhandled error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Internal error: " + ex.getMessage()));
    }

    @ExceptionHandler(it.egeos.cut3g.airgap.exceptions.SequenceMismatchException.class)
    public ResponseEntity<?> handleSequenceMismatch(it.egeos.cut3g.airgap.exceptions.SequenceMismatchException ex) {
        return ResponseEntity.badRequest().body(java.util.Map.of("error", "SEQUENCE_MISMATCH", "message", ex.getMessage()));
    }

    @ExceptionHandler(ClientAbortException.class)
    public void handleClientAbort(ClientAbortException ex) {

        log.warn("Client disconnected : {}", ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleMaxUpload(MaxUploadSizeExceededException ex) {
        log.error("Upload file too large", ex);
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("success", false, "message", "File too large for upload configuration"));
    }
}
