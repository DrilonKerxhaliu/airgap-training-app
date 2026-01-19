package it.egeos.cut3g.airgap.exceptions;

public class NoNewFilesToPackageException extends RuntimeException {
    public NoNewFilesToPackageException() {
        super("No NEW files to package");
    }
}
