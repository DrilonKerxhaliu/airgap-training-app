package it.egeos.cut3g.airgap.exceptions;

public class DownstreamIOException extends RuntimeException {
    public DownstreamIOException(String message, Throwable cause) {
        super(message, cause);
    }
}