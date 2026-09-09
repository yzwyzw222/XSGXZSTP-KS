package com.aacv.system.ingestion.application;

import com.aacv.system.ingestion.domain.NormalizedWork;
import com.aacv.system.ingestion.domain.NormalizedWork.DatePrecision;
import com.aacv.system.ingestion.domain.NormalizedWork.NormalizedAuthorship;
import com.aacv.system.ingestion.domain.NormalizedWork.NormalizedOrganization;
import com.aacv.system.ingestion.domain.NormalizedWork.NormalizedTopic;
import com.aacv.system.ingestion.domain.NormalizedWork.NormalizedVenue;
import com.aacv.system.source.domain.SourceWork;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SourceWorkNormalizer {

    private static final Pattern DOI_PATTERN = Pattern.compile("^10\\.\\d{4,9}/\\S+$");
    private static final Pattern ORCID_PATTERN = Pattern.compile("^\\d{4}-\\d{4}-\\d{4}-[\\dX]{4}$");
    private static final Pattern ISSN_PATTERN = Pattern.compile("^\\d{4}-[\\dX]{4}$");

    public NormalizedWork normalize(SourceWork source) {
        String title = normalizeText(source.title(), 1_000, "标题");
        String doi = normalizeDoi(source.doi());
        List<NormalizedAuthorship> authorships = source.authorships().stream()
                .map(this::normalizeAuthorship)
                .toList();
        List<NormalizedTopic> topics = source.topics().stream()
                .map(topic -> new NormalizedTopic(
                        topic.externalId(),
                        normalizeText(topic.displayName(), 500, "主题名称"),
                        normalizeText(topic.subfieldName(), 255, "子领域名称"),
                        normalizeText(topic.fieldName(), 255, "领域名称")))
                .toList();
        String type = normalizeText(source.type(), 64, "成果类型");
        String normalizedType = type == null ? "UNSPECIFIED" : type.toLowerCase(Locale.ROOT);
        String language = normalizeText(source.language(), 16, "语言");
        return new NormalizedWork(
                source.externalId(), doi, source.title(), title, normalizedType,
                language == null ? null : language.toLowerCase(Locale.ROOT),
                source.publicationDate(), normalizeDatePrecision(source),
                fingerprint(title, source.publicationDate() == null ? null : source.publicationDate().getYear(), authorships),
                normalizeVenue(source.primaryVenue()), authorships, topics, source.referencedWorkIds(),
                source.abstractText(), source.authorshipsMayBeIncomplete(), source.fieldWarnings(), source.scholarlyMetadata());
    }

    private NormalizedAuthorship normalizeAuthorship(SourceWork.SourceAuthorship authorship) {
        return new NormalizedAuthorship(
                authorship.position(), authorship.authorExternalId(),
                normalizeText(authorship.authorDisplayName(), 500, "作者名称"),
                normalizeOrcid(authorship.orcid()),
                authorship.organizations().stream()
                        .map(organization -> new NormalizedOrganization(
                                normalizeOrganizationExternalId(organization.externalId()),
                                normalizeText(organization.displayName(), 500, "机构名称"),
                                normalizeCountryCode(organization.countryCode()),
                                normalizeText(organization.type(), 64, "机构类型"),
                                normalizeRor(organization.rorId())))
                        .toList());
    }

    private DatePrecision normalizeDatePrecision(SourceWork source) {
        if (source.publicationDatePrecision() == null) {
            return null;
        }
        return DatePrecision.valueOf(source.publicationDatePrecision().name());
    }

    private NormalizedVenue normalizeVenue(SourceWork.SourceVenue venue) {
        if (venue == null) {
            return null;
        }
        String issn = normalizeText(venue.issnL(), 16, "ISSN-L");
        if (issn != null) {
            issn = issn.toUpperCase(Locale.ROOT);
            if (!ISSN_PATTERN.matcher(issn).matches() || !validIssnChecksum(issn)) {
                issn = null;
            }
        }
        return new NormalizedVenue(
                venue.externalId(), normalizeText(venue.displayName(), 500, "载体名称"),
                issn, normalizeText(venue.type(), 64, "载体类型"));
    }

    String normalizeDoi(String value) {
        String normalized = normalizeText(value, 512, "DOI");
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("https://doi.org/")) {
            normalized = normalized.substring("https://doi.org/".length());
        } else if (normalized.startsWith("http://doi.org/")) {
            normalized = normalized.substring("http://doi.org/".length());
        } else if (normalized.startsWith("doi:")) {
            normalized = normalized.substring(4).trim();
        }
        if (normalized.length() > 255 || !DOI_PATTERN.matcher(normalized).matches()) {
            return null;
        }
        return normalized;
    }

    String normalizeOrcid(String value) {
        String normalized = normalizeText(value, 64, "ORCID");
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT)
                .replace("HTTPS://ORCID.ORG/", "")
                .replace("HTTP://ORCID.ORG/", "");
        if (!ORCID_PATTERN.matcher(normalized).matches() || !validOrcidChecksum(normalized)) {
            return null;
        }
        return normalized;
    }

    private boolean validOrcidChecksum(String orcid) {
        String compact = orcid.replace("-", "");
        int total = 0;
        for (int index = 0; index < 15; index++) {
            total = (total + Character.digit(compact.charAt(index), 10)) * 2;
        }
        int remainder = total % 11;
        int result = (12 - remainder) % 11;
        char expected = result == 10 ? 'X' : Character.forDigit(result, 10);
        return compact.charAt(15) == expected;
    }

    private boolean validIssnChecksum(String issn) {
        String compact = issn.replace("-", "");
        int total = 0;
        for (int index = 0; index < 7; index++) {
            total += Character.digit(compact.charAt(index), 10) * (8 - index);
        }
        int result = (11 - total % 11) % 11;
        char expected = result == 10 ? 'X' : Character.forDigit(result, 10);
        return compact.charAt(7) == expected;
    }

    private String normalizeOrganizationExternalId(String value) {
        String normalized = normalizeText(value, 255, "机构外部标识");
        if (normalized == null) {
            return null;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://ror.org/") || lower.matches("0[a-hj-km-np-tv-z0-9]{6}[0-9]{2}")) {
            String identifier = lower.startsWith("https://ror.org/")
                    ? lower.substring("https://ror.org/".length()) : lower;
            return identifier.matches("0[a-hj-km-np-tv-z0-9]{6}[0-9]{2}")
                    ? "https://ror.org/" + identifier : null;
        }
        return normalized;
    }

    private String normalizeRor(String value) {
        String normalized = normalizeText(value, 128, "ROR");
        if (normalized == null) {
            return null;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        String identifier = lower.startsWith("https://ror.org/")
                ? lower.substring("https://ror.org/".length()) : lower;
        return identifier.matches("0[a-hj-km-np-tv-z0-9]{6}[0-9]{2}")
                ? "https://ror.org/" + identifier : null;
    }

    private String normalizeCountryCode(String value) {
        String normalized = normalizeText(value, 2, "国家代码");
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        return normalized.matches("[A-Z]{2}") ? normalized : null;
    }

    String normalizeText(String value, int maxLength, String fieldName) {
        if (value == null) {
            return null;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim()
                .replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "超过存储长度限制");
        }
        return normalized;
    }

    private String fingerprint(
            String title, Integer publicationYear, List<NormalizedAuthorship> authorships) {
        String authorSummary = authorships.stream()
                .map(author -> author.orcid() != null
                        ? "orcid:" + author.orcid()
                        : "name:" + (author.displayName() == null
                                ? "" : author.displayName().toLowerCase(Locale.ROOT)))
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
        String material = (title == null ? "" : title.toLowerCase(Locale.ROOT))
                + "\n" + (publicationYear == null ? "" : publicationYear)
                + "\n" + authorSummary;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }
}
