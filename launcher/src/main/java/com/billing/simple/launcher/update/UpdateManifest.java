package com.billing.simple.launcher.update;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable, cryptographically authenticated OTA update manifest representation.
 * Validates strict schema requirements, path safety, and Ed25519 publisher signature.
 */
public final class UpdateManifest {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final long MAX_ARTIFACT_SIZE_BYTES = 250_000_000L; // 250MB limit
    public static final long MIN_ARTIFACT_SIZE_BYTES = 10_000L;      // 10KB minimum
    public static final String DEFAULT_NO_NOTES = "Release notes are not available for this version.";

    private static final Pattern SHA256_PATTERN = Pattern.compile("^[a-fA-F0-9]{64}$");

    private final int schemaVersion;
    private final String appVersion;
    private final String releaseTag;
    private final String publishedAt;
    private final String minLauncherVersion;
    private final String artifactFilename;
    private final String artifactUrl;
    private final String artifactSha256;
    private final long artifactSizeBytes;
    private final String releaseNotes;

    public UpdateManifest(int schemaVersion,
                          String appVersion,
                          String releaseTag,
                          String publishedAt,
                          String minLauncherVersion,
                          String artifactFilename,
                          String artifactUrl,
                          String artifactSha256,
                          long artifactSizeBytes,
                          String releaseNotes) {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported manifest schemaVersion: " + schemaVersion);
        }
        this.schemaVersion = schemaVersion;
        this.appVersion = sanitizeRequired(appVersion, "appVersion");
        this.releaseTag = releaseTag != null ? releaseTag.trim() : "v" + this.appVersion;
        this.publishedAt = publishedAt != null ? publishedAt.trim() : Instant.now().toString();
        this.minLauncherVersion = minLauncherVersion != null ? minLauncherVersion.trim() : "1.0.0";

        // Security check: artifactFilename must not contain path traversal characters
        String cleanFilename = sanitizeRequired(artifactFilename, "artifactFilename");
        if (cleanFilename.contains("/") || cleanFilename.contains("\\") || cleanFilename.contains("..") || cleanFilename.contains("\0")) {
            throw new SecurityException("Illegal artifactFilename with path traversal or invalid characters: " + cleanFilename);
        }
        if (!cleanFilename.endsWith(".war") || cleanFilename.equalsIgnoreCase(".war")) {
            throw new IllegalArgumentException("artifactFilename must have a valid non-empty .war name: " + cleanFilename);
        }
        this.artifactFilename = cleanFilename;

        String cleanUrl = sanitizeRequired(artifactUrl, "artifactUrl");
        URI parsedUri;
        try {
            parsedUri = new URI(cleanUrl);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("artifactUrl is not a valid URI: " + cleanUrl, e);
        }
        String scheme = parsedUri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http"))) {
            throw new SecurityException("artifactUrl must use HTTPS or HTTP scheme: " + cleanUrl);
        }
        String host = parsedUri.getHost();
        if ("http".equalsIgnoreCase(scheme)) {
            if (host == null || (!host.equals("127.0.0.1") && !host.equals("localhost") && !host.equals("::1"))) {
                throw new SecurityException("Plaintext HTTP artifactUrl is prohibited for remote hosts: " + cleanUrl);
            }
        }
        this.artifactUrl = cleanUrl;

        String hash = sanitizeRequired(artifactSha256, "artifactSha256").toLowerCase();
        if (!SHA256_PATTERN.matcher(hash).matches()) {
            throw new IllegalArgumentException("Invalid artifactSha256 hex digest: " + hash);
        }
        this.artifactSha256 = hash;

        if (artifactSizeBytes < MIN_ARTIFACT_SIZE_BYTES || artifactSizeBytes > MAX_ARTIFACT_SIZE_BYTES) {
            throw new IllegalArgumentException("artifactSizeBytes out of bounds [" + MIN_ARTIFACT_SIZE_BYTES + " .. " + MAX_ARTIFACT_SIZE_BYTES + "]: " + artifactSizeBytes);
        }
        this.artifactSizeBytes = artifactSizeBytes;

        if (releaseNotes == null || releaseNotes.trim().isEmpty()) {
            this.releaseNotes = DEFAULT_NO_NOTES;
        } else {
            this.releaseNotes = releaseNotes.trim();
        }
    }

    private static String sanitizeRequired(String val, String field) {
        if (val == null || val.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required manifest field: " + field);
        }
        return val.trim();
    }

    public int getSchemaVersion() { return schemaVersion; }
    public String getAppVersion() { return appVersion; }
    public String getReleaseTag() { return releaseTag; }
    public String getPublishedAt() { return publishedAt; }
    public String getMinLauncherVersion() { return minLauncherVersion; }
    public String getArtifactFilename() { return artifactFilename; }
    public String getArtifactUrl() { return artifactUrl; }
    public String getArtifactSha256() { return artifactSha256; }
    public long getArtifactSizeBytes() { return artifactSizeBytes; }
    public String getReleaseNotes() { return releaseNotes; }

    /**
     * Serializes this manifest to canonical UTF-8 JSON.
     */
    public String toJson() {
        return "{\n" +
                "  \"schemaVersion\": " + schemaVersion + ",\n" +
                "  \"appVersion\": \"" + escapeJson(appVersion) + "\",\n" +
                "  \"releaseTag\": \"" + escapeJson(releaseTag) + "\",\n" +
                "  \"publishedAt\": \"" + escapeJson(publishedAt) + "\",\n" +
                "  \"minLauncherVersion\": \"" + escapeJson(minLauncherVersion) + "\",\n" +
                "  \"artifact\": {\n" +
                "    \"filename\": \"" + escapeJson(artifactFilename) + "\",\n" +
                "    \"url\": \"" + escapeJson(artifactUrl) + "\",\n" +
                "    \"sha256\": \"" + artifactSha256 + "\",\n" +
                "    \"sizeBytes\": " + artifactSizeBytes + "\n" +
                "  },\n" +
                "  \"releaseNotes\": \"" + escapeJson(releaseNotes) + "\"\n" +
                "}\n";
    }

    /**
     * Cryptographically authenticates the raw manifest bytes against the detached Ed25519 signature
     * and trusted public key, then parses and validates the schema.
     */
    public static UpdateManifest parseAndVerify(byte[] manifestBytes, String signatureBase64, PublicKey trustedKey) throws GeneralSecurityException {
        if (!UpdateCrypto.verifySignature(manifestBytes, signatureBase64, trustedKey)) {
            throw new GeneralSecurityException("Ed25519 signature verification failed for update manifest");
        }
        String json = new String(manifestBytes, StandardCharsets.UTF_8);
        return parse(json);
    }

    /**
     * Parses manifest JSON string without signature verification (used after signature is checked).
     */
    public static UpdateManifest parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("Manifest JSON must not be empty");
        }
        if (json.startsWith("\uFEFF")) {
            json = json.substring(1);
        }

        int schemaVersion = extractInt(json, "schemaVersion", 1);
        String appVersion = extractString(json, "appVersion");
        String releaseTag = extractString(json, "releaseTag");
        String publishedAt = extractString(json, "publishedAt");
        String minLauncherVersion = extractString(json, "minLauncherVersion");
        String filename = extractNestedString(json, "artifact", "filename");
        String url = extractNestedString(json, "artifact", "url");
        String sha256 = extractNestedString(json, "artifact", "sha256");
        long sizeBytes = extractNestedLong(json, "artifact", "sizeBytes");
        String releaseNotes = extractString(json, "releaseNotes");

        return new UpdateManifest(
                schemaVersion,
                appVersion,
                releaseTag,
                publishedAt,
                minLauncherVersion,
                filename,
                url,
                sha256,
                sizeBytes,
                releaseNotes
        );
    }

    // --- Lightweight Robust Zero-Dependency JSON Helpers ---

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String unescapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        boolean escaped = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped) {
                switch (c) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (i + 4 < s.length()) {
                            try {
                                int hexVal = Integer.parseInt(s.substring(i + 1, i + 5), 16);
                                sb.append((char) hexVal);
                                i += 4;
                            } catch (NumberFormatException e) {
                                sb.append('u');
                            }
                        } else {
                            sb.append('u');
                        }
                        break;
                    default: sb.append(c); break;
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String extractString(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return unescapeJson(matcher.group(1));
        }
        return null;
    }

    private static int extractInt(String json, String key, int defaultValue) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private static String extractNestedString(String json, String parentKey, String childKey) {
        Pattern sectionPattern = Pattern.compile("\"" + Pattern.quote(parentKey) + "\"\\s*:\\s*\\{([^}]+)\\}");
        Matcher sectionMatcher = sectionPattern.matcher(json);
        if (sectionMatcher.find()) {
            String section = sectionMatcher.group(1);
            return extractString(section, childKey);
        }
        return null;
    }

    private static long extractNestedLong(String json, String parentKey, String childKey) {
        Pattern sectionPattern = Pattern.compile("\"" + Pattern.quote(parentKey) + "\"\\s*:\\s*\\{([^}]+)\\}");
        Matcher sectionMatcher = sectionPattern.matcher(json);
        if (sectionMatcher.find()) {
            String section = sectionMatcher.group(1);
            Pattern numPattern = Pattern.compile("\"" + Pattern.quote(childKey) + "\"\\s*:\\s*(\\d+)");
            Matcher numMatcher = numPattern.matcher(section);
            if (numMatcher.find()) {
                try {
                    return Long.parseLong(numMatcher.group(1));
                } catch (NumberFormatException ignored) {}
            }
        }
        return -1L;
    }
}
