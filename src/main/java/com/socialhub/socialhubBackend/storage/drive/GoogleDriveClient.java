package com.socialhub.socialhubBackend.storage.drive;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialhub.socialhubBackend.common.exception.BusinessException;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class GoogleDriveClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(5);
    private static final int MULTIPART_UPLOAD_LIMIT_BYTES = 5 * 1024 * 1024;

    private final RestClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GoogleDriveProperties properties;

    public GoogleDriveClient(GoogleDriveProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        requestFactory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        this.client = RestClient.builder().requestFactory(requestFactory).build();
    }

    public TokenResponse exchangeAuthorizationCode(
            String code, String redirectUri, String clientId, String clientSecret) {
        MultiValueMap<String, String> form = tokenForm(clientId, clientSecret);
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");
        return call(
                () -> client.post()
                        .uri(properties.tokenUrl())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(TokenResponse.class),
                "exchange Google Drive authorization code");
    }

    public TokenResponse refreshAccessToken(String refreshToken, String clientId, String clientSecret) {
        MultiValueMap<String, String> form = tokenForm(clientId, clientSecret);
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");
        return call(
                () -> client.post()
                        .uri(properties.tokenUrl())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(TokenResponse.class),
                "refresh Google Drive token");
    }

    public UserInfo getUserInfo(String accessToken) {
        URI uri = UriComponentsBuilder.fromUriString(properties.userInfoUrl()).build().toUri();
        return call(
                () -> client.get()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .retrieve()
                        .body(UserInfo.class),
                "load Google account profile");
    }

    public AboutResponse getAbout(String accessToken) {
        URI uri = UriComponentsBuilder.fromUriString(properties.driveBaseUrl())
                .path("/about")
                .queryParam("fields", "user,storageQuota")
                .build()
                .toUri();
        return call(
                () -> client.get()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .retrieve()
                        .body(AboutResponse.class),
                "test Google Drive connection");
    }

    public FilesResponse listFiles(String accessToken) {
        URI uri = UriComponentsBuilder.fromUriString(properties.driveBaseUrl())
                .path("/files")
                .queryParam("pageSize", 50)
                .queryParam("fields", "files(id,name,mimeType,webViewLink,webContentLink,thumbnailLink,size,createdTime,parents)")
                .queryParam("orderBy", "createdTime desc")
                .build()
                .toUri();
        return call(
                () -> client.get()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .retrieve()
                        .body(FilesResponse.class),
                "list Google Drive media files");
    }

    public DriveFile uploadFile(String accessToken, MultipartFile file) {
        try {
            String filename = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                    ? "socialhub-media"
                    : file.getOriginalFilename();
            String contentType = file.getContentType() == null || file.getContentType().isBlank()
                    ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                    : file.getContentType();
            return uploadFile(accessToken, filename, contentType, file.getBytes(), null);
        } catch (IOException ex) {
            throw new BusinessException("Could not read the media file for Google Drive upload.");
        }
    }

    public DriveFile uploadFile(String accessToken, String filename, String contentType, byte[] bytes) {
        return uploadFile(accessToken, filename, contentType, bytes, null);
    }

    public DriveFile uploadFile(
            String accessToken, String filename, String contentType, byte[] bytes, String parentFolderId) {
        if (bytes.length > MULTIPART_UPLOAD_LIMIT_BYTES) {
            return uploadFileResumable(accessToken, filename, contentType, bytes, parentFolderId);
        }
        String boundary = "socialhub-drive-" + Instant.now().toEpochMilli();
        byte[] body = multipartRelatedBody(filename, contentType, bytes, boundary, parentFolderId);
        URI uri = UriComponentsBuilder.fromUriString(properties.uploadBaseUrl())
                .path("/files")
                .queryParam("uploadType", "multipart")
                .queryParam("fields", "id,name,mimeType,webViewLink,webContentLink,thumbnailLink,size,createdTime,parents")
                .build()
                .toUri();
        return call(
                () -> client.post()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header(HttpHeaders.CONTENT_TYPE, "multipart/related; boundary=" + boundary)
                        .body(body)
                        .retrieve()
                        .body(DriveFile.class),
                "upload media to Google Drive");
    }

    private DriveFile uploadFileResumable(
            String accessToken, String filename, String contentType, byte[] bytes, String parentFolderId) {
        URI initUri = UriComponentsBuilder.fromUriString(properties.uploadBaseUrl())
                .path("/files")
                .queryParam("uploadType", "resumable")
                .queryParam("fields", "id,name,mimeType,webViewLink,webContentLink,thumbnailLink,size,createdTime,parents")
                .build()
                .toUri();
        UploadMetadata metadata = new UploadMetadata(
                filename,
                parentFolderId == null || parentFolderId.isBlank() ? null : List.of(parentFolderId));
        ResponseEntity<Void> initResponse = call(
                () -> client.post()
                        .uri(initUri)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("X-Upload-Content-Type", contentType)
                        .header("X-Upload-Content-Length", String.valueOf(bytes.length))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(metadata)
                        .retrieve()
                        .toBodilessEntity(),
                "start resumable Google Drive upload");
        URI uploadUri = initResponse.getHeaders().getLocation();
        if (uploadUri == null) {
            throw new BusinessException("Google Drive did not return an upload session.");
        }
        return call(
                () -> client.put()
                        .uri(uploadUri)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header(HttpHeaders.CONTENT_TYPE, contentType)
                        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(bytes.length))
                        .body(bytes)
                        .retrieve()
                        .body(DriveFile.class),
                "upload media to Google Drive");
    }

    public DriveFile createFolder(String accessToken, String name) {
        return createFolder(accessToken, name, null);
    }

    public DriveFile createFolder(String accessToken, String name, String parentFolderId) {
        URI uri = UriComponentsBuilder.fromUriString(properties.driveBaseUrl())
                .path("/files")
                .queryParam("fields", "id,name,mimeType,webViewLink,webContentLink,thumbnailLink,size,createdTime,parents")
                .build()
                .toUri();
        CreateFileMetadata metadata = new CreateFileMetadata(
                name,
                "application/vnd.google-apps.folder",
                parentFolderId == null || parentFolderId.isBlank() ? null : List.of(parentFolderId));
        return call(
                () -> client.post()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(metadata)
                        .retrieve()
                        .body(DriveFile.class),
                "create Google Drive folder");
    }

    public DriveFile getFile(String accessToken, String fileId) {
        URI uri = UriComponentsBuilder.fromUriString(properties.driveBaseUrl())
                .path("/files/{fileId}")
                .queryParam("fields", "id,name,mimeType,webViewLink,webContentLink,thumbnailLink,size,createdTime")
                .build(fileId);
        return call(
                () -> client.get()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .retrieve()
                        .body(DriveFile.class),
                "read Google Drive file metadata");
    }

    public DownloadedFile downloadFile(String accessToken, String fileId) {
        URI uri = UriComponentsBuilder.fromUriString(properties.driveBaseUrl())
                .path("/files/{fileId}")
                .queryParam("alt", "media")
                .build(fileId);
        ResponseEntity<byte[]> response = call(
                () -> client.get()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .retrieve()
                        .toEntity(byte[].class),
                "download media from Google Drive");
        MediaType contentType = response.getHeaders().getContentType();
        return new DownloadedFile(
                response.getBody() == null ? new byte[0] : response.getBody(),
                contentType == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType.toString());
    }

    public void deleteFile(String accessToken, String fileId) {
        URI uri = UriComponentsBuilder.fromUriString(properties.driveBaseUrl())
                .path("/files/{fileId}")
                .build(fileId);
        call(
                () -> {
                    client.delete()
                            .uri(uri)
                            .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                            .retrieve()
                            .toBodilessEntity();
                    return null;
                },
                "delete media from Google Drive");
    }

    private byte[] multipartRelatedBody(
            String filename, String contentType, byte[] fileBytes, String boundary, String parentFolderId) {
        try {
            String metadataJson = objectMapper.writeValueAsString(new UploadMetadata(
                    filename,
                    parentFolderId == null || parentFolderId.isBlank() ? null : List.of(parentFolderId)));
            byte[] metadata = (
                    "--" + boundary + "\r\n"
                            + "Content-Type: application/json; charset=UTF-8\r\n\r\n"
                            + metadataJson + "\r\n"
                            + "--" + boundary + "\r\n"
                            + "Content-Type: " + contentType + "\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8);
            byte[] closing = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
            byte[] body = new byte[metadata.length + fileBytes.length + closing.length];
            System.arraycopy(metadata, 0, body, 0, metadata.length);
            System.arraycopy(fileBytes, 0, body, metadata.length, fileBytes.length);
            System.arraycopy(closing, 0, body, metadata.length + fileBytes.length, closing.length);
            return body;
        } catch (IOException ex) {
            throw new BusinessException("Could not read the media file for Google Drive upload.");
        }
    }

    private MultiValueMap<String, String> tokenForm(String clientId, String clientSecret) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        return form;
    }

    private <T> T call(Supplier<T> supplier, String action) {
        try {
            return supplier.get();
        } catch (RestClientResponseException ex) {
            throw mapGoogleError(action, ex);
        } catch (ResourceAccessException ex) {
            Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);
            log.warn("Could not reach Google Drive while trying to {}: {}", action, root.getMessage());
            throw new BusinessException("Could not reach Google Drive. Try again later.", HttpStatus.BAD_GATEWAY);
        } catch (RestClientException ex) {
            log.warn("Failed to process Google Drive response while trying to {}: {}", action, ex.getMessage());
            throw new BusinessException("Google Drive returned an unreadable response.", HttpStatus.BAD_GATEWAY);
        }
    }

    private RuntimeException mapGoogleError(String action, RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        log.warn("Google Drive API error while trying to {}: status={}, body={}",
                action, ex.getStatusCode().value(), body);
        String lower = body == null ? "" : body.toLowerCase();
        if (ex.getStatusCode().value() == 401 || lower.contains("invalid_grant")) {
            return new GoogleDriveAuthException(
                    "Google Drive token expired or was revoked. Reconnect Google Drive.");
        }
        if (ex.getStatusCode().value() == 403) {
            if (lower.contains("storagequotaexceeded") || lower.contains("quota")) {
                return new BusinessException("Google Drive quota is full.", HttpStatus.INSUFFICIENT_STORAGE);
            }
            if (lower.contains("insufficient") || lower.contains("permission")) {
                return new BusinessException(
                        "Google Drive permission was denied. Reconnect and approve the requested permissions.",
                        HttpStatus.FORBIDDEN);
            }
        }
        if (ex.getStatusCode().is4xxClientError()) {
            return new BusinessException("Google Drive rejected the request. Check permissions and try again.");
        }
        return new BusinessException("Google Drive is unavailable. Try again later.", HttpStatus.BAD_GATEWAY);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record UploadMetadata(String name, List<String> parents) {}

    private record CreateFileMetadata(String name, String mimeType, List<String> parents) {}

    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("token_type") String tokenType,
            String scope) {}

    public record UserInfo(String id, String email, String name) {}

    public record AboutResponse(DriveUser user, StorageQuota storageQuota) {}

    public record DriveUser(String displayName, String emailAddress) {}

    public record StorageQuota(String limit, String usage) {}

    public record FilesResponse(List<DriveFile> files) {}

    public record DriveFile(
            String id,
            String name,
            String mimeType,
            String webViewLink,
            String webContentLink,
            String thumbnailLink,
            String size,
            Instant createdTime,
            List<String> parents) {}

    public record DownloadedFile(byte[] body, String contentType) {}
}
