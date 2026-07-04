package com.socialhub.socialhubBackend.post.service;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.post.domain.PostMediaType;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Validates and classifies public media URLs before they are stored/published. */
@Component
public class MediaUrlValidator {

    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final Map<String, PostMediaType> EXTENSIONS = Map.ofEntries(
            Map.entry("jpg", PostMediaType.IMAGE),
            Map.entry("jpeg", PostMediaType.IMAGE),
            Map.entry("png", PostMediaType.IMAGE),
            Map.entry("webp", PostMediaType.IMAGE),
            Map.entry("gif", PostMediaType.IMAGE),
            Map.entry("mp4", PostMediaType.VIDEO),
            Map.entry("mov", PostMediaType.VIDEO),
            Map.entry("avi", PostMediaType.VIDEO),
            Map.entry("webm", PostMediaType.VIDEO));

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(TIMEOUT)
            .build();

    public PostMediaType validate(String mediaUrl) {
        String url = mediaUrl == null ? null : mediaUrl.trim();
        if (url == null || url.isBlank()) {
            return null;
        }
        URI uri = parsePublicUri(url);
        PostMediaType extensionType = detectByExtension(uri);
        MediaProbe probe = probe(uri);
        PostMediaType contentType = detectByContentType(probe.contentType());
        if (probe.contentType() != null && contentType == null && !isGenericBinary(probe.contentType())) {
            throw new BusinessException("Media URL returned an unsupported HTTP content type: " + probe.contentType());
        }
        PostMediaType resolved = contentType != null ? contentType : extensionType;
        if (resolved == null) {
            throw new BusinessException(
                    "Unsupported media URL. Supported images: jpg, jpeg, png, webp, gif. "
                            + "Supported videos: mp4, mov, avi, webm.");
        }
        if (extensionType != null && contentType != null && extensionType != contentType) {
            throw new BusinessException("Media URL extension does not match its HTTP content type.");
        }
        return resolved;
    }

    private URI parsePublicUri(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Media URL is not a valid URL.");
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new BusinessException("Media URL must be a public http or https URL.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new BusinessException("Media URL must include a public host.");
        }
        return uri;
    }

    private MediaProbe probe(URI uri) {
        HttpRequest head = HttpRequest.newBuilder(uri).timeout(TIMEOUT).method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
        try {
            HttpResponse<Void> response = httpClient.send(head, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 405 || response.statusCode() == 403) {
                return getProbe(uri);
            }
            return validateProbe(response.statusCode(), contentType(response));
        } catch (Exception ignored) {
            return getProbe(uri);
        }
    }

    private MediaProbe getProbe(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("Range", "bytes=0-0")
                .GET()
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return validateProbe(response.statusCode(), contentType(response));
        } catch (Exception ex) {
            throw new BusinessException(
                    "Media URL is broken, private, or inaccessible from the server.",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private MediaProbe validateProbe(int status, String contentType) {
        if (status < 200 || status >= 400) {
            throw new BusinessException(
                    "Media URL is broken, private, or inaccessible from the server (HTTP " + status + ").");
        }
        return new MediaProbe(contentType);
    }

    private String contentType(HttpResponse<?> response) {
        return response.headers().firstValue("content-type").orElse(null);
    }

    private PostMediaType detectByExtension(URI uri) {
        String path = Optional.ofNullable(uri.getPath()).orElse("");
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) {
            return null;
        }
        String ext = path.substring(dot + 1).toLowerCase(Locale.ROOT);
        return EXTENSIONS.get(ext);
    }

    private PostMediaType detectByContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        if (lower.startsWith("image/jpeg")
                || lower.startsWith("image/png")
                || lower.startsWith("image/webp")
                || lower.startsWith("image/gif")) {
            return PostMediaType.IMAGE;
        }
        if (lower.startsWith("video/mp4")
                || lower.startsWith("video/quicktime")
                || lower.startsWith("video/x-msvideo")
                || lower.startsWith("video/webm")
                || lower.startsWith("video/avi")) {
            return PostMediaType.VIDEO;
        }
        return null;
    }

    private boolean isGenericBinary(String contentType) {
        String lower = contentType.toLowerCase(Locale.ROOT);
        return lower.startsWith("application/octet-stream") || lower.startsWith("binary/octet-stream");
    }

    private record MediaProbe(String contentType) {}
}
