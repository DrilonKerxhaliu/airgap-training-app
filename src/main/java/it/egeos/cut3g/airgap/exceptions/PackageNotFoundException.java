package it.egeos.cut3g.airgap.exceptions;

public class PackageNotFoundException extends RuntimeException {
    public PackageNotFoundException(String id) {
        super("Package not found: " + id);
    }
}

