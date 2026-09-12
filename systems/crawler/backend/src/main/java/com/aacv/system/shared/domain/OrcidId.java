package com.aacv.system.shared.domain;

import java.util.Locale;

public final class OrcidId {
    private OrcidId() {}

    public static String normalize(String value) {
        if (value == null) return null;
        String id = value.strip().toUpperCase(Locale.ROOT).replaceFirst("^HTTPS?://ORCID\\.ORG/", "");
        if (!id.matches("\\d{4}-\\d{4}-\\d{4}-\\d{3}[\\dX]")) return null;
        String digits = id.replace("-", "");
        int total = 0;
        for (int i = 0; i < 15; i++) total = (total + digits.charAt(i) - '0') * 2;
        int checksum = (12 - total % 11) % 11;
        return digits.charAt(15) == (checksum == 10 ? 'X' : (char) ('0' + checksum)) ? id : null;
    }
}
