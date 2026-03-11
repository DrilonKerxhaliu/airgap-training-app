package it.egeos.cut3g.airgap.service.util;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public final class PackageNameParser {

    private static final Pattern PKG_PATTERN =
            Pattern.compile("^PKG_\\d{8}T\\d{6}Z_\\d{8}T\\d{6}Z_(\\d{6})\\.tar$");

    private PackageNameParser() {
    }

    public static long extractSequence(String packageName) {
        Matcher matcher = PKG_PATTERN.matcher(packageName);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid package name format: " + packageName);
        }
        return Long.parseLong(matcher.group(1));
    }
}