package it.egeos.cut3g.airgap.exceptions;

public class PackageFileNotFoundException extends RuntimeException {
    public PackageFileNotFoundException(String path) {
        super("Package tar not found on disk: " + path);
    }
}

