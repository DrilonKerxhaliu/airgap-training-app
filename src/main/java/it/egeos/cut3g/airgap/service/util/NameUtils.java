package it.egeos.cut3g.airgap.service.util;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Naming convention:
 * PKG_<TransactionStartTime>_<TransactionStopTime>_<ProgressiveNumber>.tar
 */
public class NameUtils {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(java.time.ZoneOffset.UTC);

    public static String packageName(Instant start, Instant stop, long progressiveNumber) {
        return String.format("PKG_%s_%s_%06d.tar", FMT.format(start), FMT.format(stop), progressiveNumber);
    }

    public static String dataTarName(String packageName) {
        return packageName.replace(".tar", "-data.tar");
    }
}
