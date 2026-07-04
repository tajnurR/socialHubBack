package com.socialhub.socialhubBackend.media.service;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.common.exception.ResourceNotFoundException;
import com.socialhub.socialhubBackend.media.domain.MediaAsset;
import com.socialhub.socialhubBackend.media.domain.MediaType;
import com.socialhub.socialhubBackend.media.domain.MediaUploadStatus;
import com.socialhub.socialhubBackend.media.dto.MediaDtos.MediaBulkUploadResult;
import com.socialhub.socialhubBackend.media.dto.MediaDtos.MediaItemResponse;
import com.socialhub.socialhubBackend.media.dto.MediaDtos.MediaUploadItemResult;
import com.socialhub.socialhubBackend.media.repository.MediaAssetRepository;
import com.socialhub.socialhubBackend.post.repository.PostRepository;
import com.socialhub.socialhubBackend.storage.drive.GoogleDriveClient.DownloadedFile;
import com.socialhub.socialhubBackend.storage.drive.GoogleDriveClient.DriveFile;
import com.socialhub.socialhubBackend.storage.drive.GoogleDriveService;
import com.socialhub.socialhubBackend.user.context.CurrentUser;
import com.socialhub.socialhubBackend.user.context.CurrentUserProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaService {

    private static final List<String> EXPORT_COLUMNS = List.of(
            "mediaId",
            "fileName",
            "mediaType",
            "fileSize",
            "googleDriveFileId",
            "googleDriveUrl",
            "uploadStatus",
            "createdAt");

    private final MediaAssetRepository mediaRepository;
    private final PostRepository postRepository;
    private final GoogleDriveService googleDriveService;
    private final CurrentUserProvider currentUserProvider;

    public MediaService(
            MediaAssetRepository mediaRepository,
            PostRepository postRepository,
            GoogleDriveService googleDriveService,
            CurrentUserProvider currentUserProvider) {
        this.mediaRepository = mediaRepository;
        this.postRepository = postRepository;
        this.googleDriveService = googleDriveService;
        this.currentUserProvider = currentUserProvider;
    }

    public List<MediaItemResponse> list(String filter) {
        CurrentUser user = currentUserProvider.currentUser();
        return mediaRepository
                .findAll(mediaSpecification(user, normalizeFilter(filter)), Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public MediaBulkUploadResult upload(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new BusinessException("Choose at least one media file to upload.");
        }
        List<MediaUploadItemResult> items = new ArrayList<>();
        int uploaded = 0;
        int failed = 0;
        int duplicate = 0;
        for (MultipartFile file : files) {
            MediaUploadItemResult result = uploadOne(file);
            items.add(result);
            if (result.duplicate()) {
                duplicate++;
            }
            if (result.uploaded()) {
                uploaded++;
            } else {
                failed++;
            }
        }
        int progress = files.length == 0 ? 0 : (int) Math.round((uploaded + failed) * 100.0 / files.length);
        return new MediaBulkUploadResult(files.length, uploaded, failed, duplicate, progress, items);
    }

    public MediaItemResponse retry(Long id, MultipartFile file) {
        MediaAsset asset = getOwned(id);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Choose the original file to retry the upload.");
        }
        FileInfo info = fileInfo(file);
        if (!asset.getChecksumSha256().equals(info.checksum()) || !asset.getFileSize().equals(info.size())) {
            throw new BusinessException("Retry file does not match the original failed media.");
        }
        asset.setUploadStatus(MediaUploadStatus.UPLOADING);
        asset.setErrorMessage(null);
        mediaRepository.save(asset);
        uploadToDrive(asset, file);
        return toResponse(asset);
    }

    public DownloadedMedia download(Long id) {
        MediaAsset asset = getOwned(id);
        if (asset.getGoogleDriveFileId() == null || asset.getGoogleDriveFileId().isBlank()) {
            throw new BusinessException("This media item is not available in Google Drive.", HttpStatus.CONFLICT);
        }
        DownloadedFile downloaded = googleDriveService.downloadMediaFile(asset.getGoogleDriveFileId());
        return new DownloadedMedia(asset.getFileName(), asset.getContentType(), downloaded.body());
    }

    public void delete(Long id) {
        MediaAsset asset = getOwned(id);
        if (asset.getGoogleDriveFileId() != null && !asset.getGoogleDriveFileId().isBlank()) {
            googleDriveService.deleteMediaFile(asset.getGoogleDriveFileId());
        }
        mediaRepository.delete(asset);
    }

    public byte[] export(String format) {
        List<MediaItemResponse> media = list("ALL");
        if ("xlsx".equalsIgnoreCase(format)) {
            return exportXlsx(media);
        }
        return exportCsv(media);
    }

    private MediaUploadItemResult uploadOne(MultipartFile file) {
        String incomingName = originalFilename(file);
        try {
            FileInfo info = fileInfo(file);
            CurrentUser user = currentUserProvider.currentUser();
            MediaAsset duplicate = mediaRepository
                    .findByOrganizationIdAndUserIdAndChecksumSha256AndFileSize(
                            user.organizationId(), user.userId(), info.checksum(), info.size())
                    .orElse(null);
            if (duplicate != null) {
                return new MediaUploadItemResult(
                        incomingName,
                        true,
                        duplicate.getUploadStatus() == MediaUploadStatus.UPLOADED,
                        duplicate.getErrorMessage(),
                        toResponse(duplicate));
            }

            MediaAsset asset = new MediaAsset();
            asset.setOrganizationId(user.organizationId());
            asset.setUserId(user.userId());
            asset.setFileName(incomingName);
            asset.setOriginalFileName(incomingName);
            asset.setMediaType(info.mediaType());
            asset.setContentType(info.contentType());
            asset.setExtension(info.extension());
            asset.setFileSize(info.size());
            asset.setChecksumSha256(info.checksum());
            asset.setUploadStatus(MediaUploadStatus.UPLOADING);
            mediaRepository.saveAndFlush(asset);
            uploadToDrive(asset, file);
            boolean ok = asset.getUploadStatus() == MediaUploadStatus.UPLOADED;
            return new MediaUploadItemResult(incomingName, false, ok, asset.getErrorMessage(), toResponse(asset));
        } catch (BusinessException ex) {
            return new MediaUploadItemResult(incomingName, false, false, ex.getMessage(), null);
        }
    }

    private void uploadToDrive(MediaAsset asset, MultipartFile file) {
        try {
            DriveFile driveFile = googleDriveService.uploadMediaFile(file);
            asset.setGoogleDriveFileId(driveFile.id());
            asset.setGoogleDriveUrl(driveFile.webViewLink());
            asset.setDirectDownloadUrl(driveFile.webContentLink());
            asset.setThumbnailUrl(driveFile.thumbnailLink());
            asset.setUploadStatus(MediaUploadStatus.UPLOADED);
            asset.setErrorMessage(null);
        } catch (BusinessException ex) {
            asset.setUploadStatus(MediaUploadStatus.FAILED);
            asset.setErrorMessage(truncate(ex.getMessage(), 1000));
        }
        mediaRepository.save(asset);
    }

    private MediaAsset getOwned(Long id) {
        CurrentUser user = currentUserProvider.currentUser();
        return mediaRepository
                .findByIdAndOrganizationIdAndUserId(id, user.organizationId(), user.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Media", id));
    }

    private FileInfo fileInfo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Choose a media file to upload.");
        }
        String filename = originalFilename(file);
        String extension = extension(filename);
        MediaType mediaType = mediaType(extension);
        String contentType = contentType(file, mediaType);
        long size = file.getSize();
        if (size <= 0) {
            throw new BusinessException("Media file is empty.");
        }
        return new FileInfo(filename, extension, contentType, mediaType, size, checksum(file));
    }

    private String checksum(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = file.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException ex) {
            throw new BusinessException("Could not read the uploaded media file.");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String originalFilename(MultipartFile file) {
        String name = file == null ? null : file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "socialhub-media";
        }
        return name.replace("\\", "/").substring(name.replace("\\", "/").lastIndexOf('/') + 1).trim();
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            throw new BusinessException("Unsupported media file. Add a valid file extension.");
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private MediaType mediaType(String extension) {
        return switch (extension) {
            case "jpg", "jpeg", "png", "webp", "gif" -> MediaType.IMAGE;
            case "mp4", "mov", "avi", "webm" -> MediaType.VIDEO;
            default -> throw new BusinessException(
                    "Unsupported media file. Supported images: jpg, jpeg, png, webp, gif. "
                            + "Supported videos: mp4, mov, avi, webm.");
        };
    }

    private String contentType(MultipartFile file, MediaType mediaType) {
        String supplied = file.getContentType();
        if (supplied != null && !supplied.isBlank()) {
            String lower = supplied.toLowerCase(Locale.ROOT);
            if ((mediaType == MediaType.IMAGE && lower.startsWith("image/"))
                    || (mediaType == MediaType.VIDEO && lower.startsWith("video/"))
                    || lower.equals("application/octet-stream")) {
                return supplied;
            }
            throw new BusinessException("File content type does not match its extension.");
        }
        return mediaType == MediaType.IMAGE ? "image/" + extension(originalFilename(file)) : "video/" + extension(originalFilename(file));
    }

    private MediaItemResponse toResponse(MediaAsset asset) {
        return new MediaItemResponse(
                asset.getId(),
                asset.getFileName(),
                asset.getOriginalFileName(),
                asset.getMediaType(),
                asset.getContentType(),
                asset.getExtension(),
                asset.getFileSize(),
                asset.getChecksumSha256(),
                asset.getGoogleDriveFileId(),
                asset.getGoogleDriveUrl(),
                asset.getDirectDownloadUrl(),
                asset.getThumbnailUrl(),
                asset.getUploadStatus(),
                asset.getErrorMessage(),
                relatedPostCount(asset),
                asset.getCreatedAt(),
                asset.getUpdatedAt());
    }

    private long relatedPostCount(MediaAsset asset) {
        List<String> urls = new ArrayList<>();
        if (asset.getGoogleDriveUrl() != null && !asset.getGoogleDriveUrl().isBlank()) {
            urls.add(asset.getGoogleDriveUrl());
        }
        if (asset.getDirectDownloadUrl() != null && !asset.getDirectDownloadUrl().isBlank()) {
            urls.add(asset.getDirectDownloadUrl());
        }
        if (urls.isEmpty()) {
            return 0;
        }
        return postRepository.countByOrganizationIdAndUserIdAndMediaUrlIn(
                asset.getOrganizationId(), asset.getUserId(), urls);
    }

    private Specification<MediaAsset> mediaSpecification(CurrentUser user, String filter) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), user.organizationId()));
            predicates.add(cb.equal(root.get("userId"), user.userId()));
            switch (filter) {
                case "IMAGES" -> predicates.add(cb.equal(root.get("mediaType"), MediaType.IMAGE));
                case "VIDEOS" -> predicates.add(cb.equal(root.get("mediaType"), MediaType.VIDEO));
                case "UPLOADED" -> predicates.add(cb.equal(root.get("uploadStatus"), MediaUploadStatus.UPLOADED));
                case "FAILED" -> predicates.add(cb.equal(root.get("uploadStatus"), MediaUploadStatus.FAILED));
                case "RECENT" -> predicates.add(cb.greaterThanOrEqualTo(
                        root.get("createdAt"), Instant.now().minus(7, ChronoUnit.DAYS)));
                default -> {
                }
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private String normalizeFilter(String filter) {
        return filter == null || filter.isBlank() ? "ALL" : filter.trim().toUpperCase(Locale.ROOT);
    }

    private byte[] exportCsv(List<MediaItemResponse> media) {
        StringBuilder out = new StringBuilder();
        out.append(String.join(",", EXPORT_COLUMNS)).append('\n');
        for (MediaItemResponse item : media) {
            out.append(csv(item.mediaId()))
                    .append(',').append(csv(item.fileName()))
                    .append(',').append(csv(item.mediaType()))
                    .append(',').append(csv(item.fileSize()))
                    .append(',').append(csv(item.googleDriveFileId()))
                    .append(',').append(csv(item.googleDriveUrl()))
                    .append(',').append(csv(item.uploadStatus()))
                    .append(',').append(csv(item.createdAt()))
                    .append('\n');
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] exportXlsx(List<MediaItemResponse> media) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Media");
            Row header = sheet.createRow(0);
            for (int i = 0; i < EXPORT_COLUMNS.size(); i++) {
                header.createCell(i).setCellValue(EXPORT_COLUMNS.get(i));
                sheet.setColumnWidth(i, 24 * 256);
            }
            int rowIndex = 1;
            for (MediaItemResponse item : media) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(item.mediaId());
                row.createCell(1).setCellValue(nullToBlank(item.fileName()));
                row.createCell(2).setCellValue(String.valueOf(item.mediaType()));
                row.createCell(3).setCellValue(item.fileSize());
                row.createCell(4).setCellValue(nullToBlank(item.googleDriveFileId()));
                row.createCell(5).setCellValue(nullToBlank(item.googleDriveUrl()));
                row.createCell(6).setCellValue(String.valueOf(item.uploadStatus()));
                row.createCell(7).setCellValue(item.createdAt() == null ? "" : item.createdAt().toString());
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException("Could not export media library.");
        }
    }

    private String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private record FileInfo(
            String fileName,
            String extension,
            String contentType,
            MediaType mediaType,
            Long size,
            String checksum) {}

    public record DownloadedMedia(String fileName, String contentType, byte[] body) {}
}
