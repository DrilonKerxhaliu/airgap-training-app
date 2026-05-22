package it.egeos.cut3g.airgap.service.util;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

public class HashUtils {

    public static String md5Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(bytes);
            return toHex(digest);
        } catch (Exception e) {
            throw new RuntimeException("Unable to compute MD5", e);
        }
    }

    public static String md5Hex(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];

            int read;
            while ((read = in.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }

            return toHex(md.digest());
        } catch (Exception e) {
            throw new RuntimeException("Unable to compute MD5 for " + file, e);
        }
    }

    private static String toHex(byte[] digest) {
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}