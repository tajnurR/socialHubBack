package com.socialhub.socialhubBackend.media.service;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.common.exception.ResourceNotFoundException;
import com.socialhub.socialhubBackend.media.domain.MediaAsset;
import com.socialhub.socialhubBackend.media.domain.MediaFolder;
import com.socialhub.socialhubBackend.media.domain.MediaType;
import com.socialhub.socialhubBackend.media.domain.MediaUploadStatus;
import com.socialhub.socialhubBackend.media.dto.MediaDtos.CreateMediaFolderRequest;
import com.socialhub.socialhubBackend.media.dto.MediaDtos.MediaBulkUploadResult;
import com.socialhub.socialhubBackend.media.dto.MediaDtos.MediaFolderResponse;
import com.socialhub.socialhubBackend.media.dto.MediaDtos.MediaItemResponse;
import com.socialhub.socialhubBackend.media.dto.MediaDtos.MediaPageResponse;
import com.socialhub.socialhubBackend.media.dto.MediaDtos.MediaUploadItemResult;
import com.socialhub.socialhubBackend.media.repository.MediaAssetRepository;
import com.socialhub.socialhubBackend.media.repository.MediaFolderRepository;
import com.socialhub.socialhubBackend.post.repository.PostRepository;
import com.socialhub.socialhubBackend.storage.drive.GoogleDriveClient.DownloadedFile;
import com.socialhub.socialhubBackend.storage.drive.GoogleDriveClient.DriveFile;
import com.socialhub.socialhubBackend.storage.drive.GoogleDriveService;
import com.socialhub.socialhubBackend.user.context.CurrentUser;
import com.socialhub.socialhubBackend.user.context.CurrentUserProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

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
    private static final Duration EXTERNAL_MEDIA_TIMEOUT = Duration.ofSeconds(20);

    private final MediaAssetRepository mediaRepository;
    private final MediaFolderRepository folderRepository;
    private final PostRepository postRepository;
    private final GoogleDriveService googleDriveService;
    private final CurrentUserProvider currentUserProvider;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(EXTERNAL_MEDIA_TIMEOUT)
            .build();

    public MediaService(
            MediaAssetRepository mediaRepository,
            MediaFolderRepository folderRepository,
            PostRepository postRepository,
            GoogleDriveService googleDriveService,
            CurrentUserProvider currentUserProvider) {
        this.mediaRepository = mediaRepository;
        this.folderRepository = folderRepository;
        this.postRepository = postRepository;
        this.googleDriveService = googleDriveService;
        this.currentUserProvider = currentUserProvider;
    }

    public List<MediaItemResponse> list(String filter, Long folderId) {
        CurrentUser user = currentUserProvider.currentUser();
        Long resolvedFolderId = resolveFolderScope(user, folderId);
        return mediaRepository
                .findAll(
                        mediaSpecification(user, normalizeFilter(filter), resolvedFolderId),
                        Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public MediaPageResponse page(
            String filter,
            Long folderId,
            String search,
            String sortOrder,
            int page,
            int size) {
        CurrentUser user = currentUserProvider.currentUser();
        Long resolvedFolderId = resolveFolderScope(user, folderId);
        int resolvedSize = Math.max(10, Math.min(size, 100));
        int resolvedPage = Math.max(page, 0);
        SearchScope searchScope = searchScope(user, search);
        Page<MediaAsset> result = mediaRepository.findAll(
                mediaSpecification(user, normalizeFilter(filter), resolvedFolderId, searchScope),
                PageRequest.of(resolvedPage, resolvedSize, sortFor(sortOrder)));
        return new MediaPageResponse(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getTotalElements(),
                result.getNumber(),
                result.getSize(),
                result.getTotalPages());
    }

    public List<MediaFolderResponse> folders() {
        CurrentUser user = currentUserProvider.currentUser();
        List<MediaFolder> folders = folderRepository.findByOrganizationIdAndUserIdOrderByNameAsc(
                user.organizationId(), user.userId());
        Map<Long, Long> counts = folderCounts(user, folders);
        return folders.stream().map(folder -> toFolderResponse(folder, counts.getOrDefault(folder.getId(), 0L))).toList();
    }

    public MediaFolderResponse createFolder(CreateMediaFolderRequest request) {
        CurrentUser user = currentUserProvider.currentUser();
        String name = request == null || request.name() == null ? "" : request.name().trim();
        if (name.isBlank()) {
            throw new BusinessException("Folder name is required.");
        }
        folderRepository
                .findByOrganizationIdAndUserIdAndNameIgnoreCase(user.organizationId(), user.userId(), name)
                .ifPresent(existing -> {
                    throw new BusinessException("A folder with this name already exists.");
                });
        DriveFile driveFolder = googleDriveService.createFolder(name);
        MediaFolder folder = new MediaFolder();
        folder.setOrganizationId(user.organizationId());
        folder.setUserId(user.userId());
        folder.setName(name);
        folder.setGoogleDriveFolderId(driveFolder.id());
        folder.setGoogleDriveUrl(driveFolder.webViewLink());
        return toFolderResponse(folderRepository.save(folder), 0);
    }

    public MediaBulkUploadResult upload(MultipartFile[] files, Long folderId) {
        if (files == null || files.length == 0) {
            throw new BusinessException("Choose at least one media file to upload.");
        }
        CurrentUser user = currentUserProvider.currentUser();
        MediaFolder folder = requireUploadFolder(user, folderId);
        List<MediaUploadItemResult> items = new ArrayList<>();
        int uploaded = 0;
        int failed = 0;
        int duplicate = 0;
        for (MultipartFile file : files) {
            MediaUploadItemResult result = uploadOne(file, folder);
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

    public MediaItemResponse importExternalMedia(String sourceUrl, MediaType expectedType) {
        CurrentUser user = currentUserProvider.currentUser();
        ImportedBinaryMedia downloaded = downloadExternalMedia(sourceUrl, expectedType);
        return createOrReuseMedia(user, downloaded, null);
    }

    public MediaItemResponse attachGoogleDriveMedia(String googleDriveUrl) {
        CurrentUser user = currentUserProvider.currentUser();
        String fileId = extractGoogleDriveFileId(googleDriveUrl);
        DriveFile driveFile = googleDriveService.getMediaFile(fileId);
        MediaAsset existing = mediaRepository
                .findByOrganizationIdAndUserIdAndGoogleDriveFileId(
                        user.organizationId(), user.userId(), fileId)
                .orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }
        ImportedBinaryMedia downloaded = downloadDriveMedia(googleDriveUrl, fileId, driveFile);
        return createOrReuseMedia(user, downloaded, driveFile);
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
        MediaFolder folder = asset.getFolderId() == null
                ? null
                : folderRepository.findByIdAndOrganizationIdAndUserId(
                                asset.getFolderId(), asset.getOrganizationId(), asset.getUserId())
                        .orElse(null);
        uploadToDrive(asset, file, folder);
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

    public byte[] export(String format, Long folderId, List<Long> mediaIds) {
        List<MediaItemResponse> media = selectedMedia(mediaIds);
        if (media.isEmpty()) {
            media = list("ALL", folderId);
        }
        if ("xlsx".equalsIgnoreCase(format)) {
            return exportXlsx(media);
        }
        return exportCsv(media);
    }

    private MediaUploadItemResult uploadOne(MultipartFile file, MediaFolder folder) {
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
            asset.setFolderId(folder == null ? null : folder.getId());
            asset.setUploadStatus(MediaUploadStatus.UPLOADING);
            mediaRepository.saveAndFlush(asset);
            uploadToDrive(asset, file, folder);
            boolean ok = asset.getUploadStatus() == MediaUploadStatus.UPLOADED;
            return new MediaUploadItemResult(incomingName, false, ok, asset.getErrorMessage(), toResponse(asset));
        } catch (BusinessException ex) {
            return new MediaUploadItemResult(incomingName, false, false, ex.getMessage(), null);
        }
    }

    private MediaItemResponse createOrReuseMedia(
            CurrentUser user,
            ImportedBinaryMedia imported,
            DriveFile existingDriveFile) {
        MediaAsset duplicate = mediaRepository
                .findByOrganizationIdAndUserIdAndChecksumSha256AndFileSize(
                        user.organizationId(), user.userId(), imported.checksum(), imported.fileSize())
                .orElse(null);
        if (duplicate != null) {
            return toResponse(duplicate);
        }

        MediaAsset asset = new MediaAsset();
        asset.setOrganizationId(user.organizationId());
        asset.setUserId(user.userId());
        asset.setFileName(imported.fileName());
        asset.setOriginalFileName(imported.originalFileName());
        asset.setMediaType(imported.mediaType());
        asset.setContentType(imported.contentType());
        asset.setExtension(imported.extension());
        asset.setFileSize(imported.fileSize());
        asset.setChecksumSha256(imported.checksum());

        if (existingDriveFile != null) {
            asset.setGoogleDriveFileId(existingDriveFile.id());
            asset.setGoogleDriveUrl(existingDriveFile.webViewLink());
            asset.setDirectDownloadUrl(existingDriveFile.webContentLink());
            asset.setThumbnailUrl(existingDriveFile.thumbnailLink());
            asset.setUploadStatus(MediaUploadStatus.UPLOADED);
            asset.setErrorMessage(null);
            return toResponse(mediaRepository.save(asset));
        }

        asset.setUploadStatus(MediaUploadStatus.UPLOADING);
        mediaRepository.saveAndFlush(asset);
        uploadToDrive(asset, imported);
        return toResponse(asset);
    }

    private void uploadToDrive(MediaAsset asset, MultipartFile file, MediaFolder folder) {
        try {
            DriveFile driveFile = folder == null
                    ? googleDriveService.uploadMediaFile(file)
                    : googleDriveService.uploadMediaFile(file, folder.getGoogleDriveFolderId());
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

    private void uploadToDrive(MediaAsset asset, ImportedBinaryMedia imported) {
        try {
            DriveFile driveFile = googleDriveService.uploadMediaBytes(
                    imported.fileName(), imported.contentType(), imported.bytes());
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

    private MediaFolder resolveFolder(CurrentUser user, Long folderId) {
        if (folderId == null || folderId == 0L) {
            return null;
        }
        return folderRepository
                .findByIdAndOrganizationIdAndUserId(folderId, user.organizationId(), user.userId())
                .orElseThrow(() -> new BusinessException("Selected folder was not found."));
    }

    private MediaFolder requireUploadFolder(CurrentUser user, Long folderId) {
        if (folderId == null || folderId == 0L) {
            throw new BusinessException("Select or create a folder before uploading media.");
        }
        return resolveFolder(user, folderId);
    }

    private Long resolveFolderScope(CurrentUser user, Long folderId) {
        if (folderId == null || folderId == 0L) {
            return folderId;
        }
        return resolveFolder(user, folderId).getId();
    }

    private List<MediaItemResponse> selectedMedia(List<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = new LinkedHashSet<>(mediaIds.stream()
                .filter(id -> id != null && id > 0)
                .toList());
        if (ids.isEmpty()) {
            return List.of();
        }
        CurrentUser user = currentUserProvider.currentUser();
        List<MediaAsset> media = mediaRepository.findByOrganizationIdAndUserIdAndIdInOrderByCreatedAtDesc(
                user.organizationId(), user.userId(), ids);
        if (media.size() != ids.size()) {
            throw new BusinessException("One or more selected media files were not found.");
        }
        return media.stream().map(this::toResponse).toList();
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

    private String checksum(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes);
            return HexFormat.of().formatHex(digest.digest());
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

    private String resolveExtension(String filename, String contentType, MediaType mediaType) {
        String normalized = filename == null ? "" : filename.trim();
        if (!normalized.isBlank()) {
            int dot = normalized.lastIndexOf('.');
            if (dot >= 0 && dot < normalized.length() - 1) {
                return normalized.substring(dot + 1).toLowerCase(Locale.ROOT);
            }
        }
        return switch (mediaType) {
            case IMAGE -> switch (contentType.toLowerCase(Locale.ROOT)) {
                case "image/jpeg" -> "jpg";
                case "image/png" -> "png";
                case "image/webp" -> "webp";
                case "image/gif" -> "gif";
                default -> throw new BusinessException("Unsupported image content type: " + contentType);
            };
            case VIDEO -> switch (contentType.toLowerCase(Locale.ROOT)) {
                case "video/mp4" -> "mp4";
                case "video/quicktime" -> "mov";
                case "video/x-msvideo", "video/avi" -> "avi";
                case "video/webm" -> "webm";
                default -> throw new BusinessException("Unsupported video content type: " + contentType);
            };
        };
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
        MediaFolder folder = asset.getFolderId() == null
                ? null
                : folderRepository.findByIdAndOrganizationIdAndUserId(
                                asset.getFolderId(), asset.getOrganizationId(), asset.getUserId())
                        .orElse(null);
        return new MediaItemResponse(
                asset.getId(),
                asset.getFileName(),
                asset.getOriginalFileName(),
                asset.getMediaType(),
                asset.getContentType(),
                asset.getExtension(),
                asset.getFileSize(),
                asset.getChecksumSha256(),
                asset.getFolderId(),
                folder == null ? null : folder.getName(),
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

    private MediaFolderResponse toFolderResponse(MediaFolder folder, long mediaCount) {
        return new MediaFolderResponse(
                folder.getId(),
                folder.getName(),
                folder.getGoogleDriveFolderId(),
                folder.getGoogleDriveUrl(),
                mediaCount,
                folder.getCreatedAt(),
                folder.getUpdatedAt());
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
            urls.add("__socialhub_no_media_url__");
        }
        return postRepository.countRelatedToMedia(
                asset.getOrganizationId(), asset.getUserId(), asset.getId(), urls);
    }

    private Specification<MediaAsset> mediaSpecification(CurrentUser user, String filter, Long folderId) {
        return mediaSpecification(user, filter, folderId, SearchScope.empty());
    }

    private Specification<MediaAsset> mediaSpecification(
            CurrentUser user,
            String filter,
            Long folderId,
            SearchScope searchScope) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), user.organizationId()));
            predicates.add(cb.equal(root.get("userId"), user.userId()));
            if (folderId != null && folderId == 0L) {
                predicates.add(cb.isNull(root.get("folderId")));
            } else if (folderId != null && folderId > 0) {
                predicates.add(cb.equal(root.get("folderId"), folderId));
            }
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
            if (searchScope.hasSearch()) {
                String pattern = "%" + searchScope.term().toLowerCase(Locale.ROOT) + "%";
                List<jakarta.persistence.criteria.Predicate> searchPredicates = new ArrayList<>();
                searchPredicates.add(cb.like(cb.lower(root.get("fileName")), pattern));
                searchPredicates.add(cb.like(cb.lower(root.get("originalFileName")), pattern));
                searchPredicates.add(cb.like(cb.lower(root.get("contentType")), pattern));
                searchPredicates.add(cb.like(cb.lower(root.get("extension")), pattern));
                searchPredicates.add(cb.like(cb.lower(root.get("uploadStatus").as(String.class)), pattern));
                if (!searchScope.folderIds().isEmpty()) {
                    searchPredicates.add(root.get("folderId").in(searchScope.folderIds()));
                }
                predicates.add(cb.or(searchPredicates.toArray(jakarta.persistence.criteria.Predicate[]::new)));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private String normalizeFilter(String filter) {
        return filter == null || filter.isBlank() ? "ALL" : filter.trim().toUpperCase(Locale.ROOT);
    }

    private SearchScope searchScope(CurrentUser user, String search) {
        String term = search == null ? "" : search.trim();
        if (term.isBlank()) {
            return SearchScope.empty();
        }
        List<Long> folderIds = folderRepository
                .findByOrganizationIdAndUserIdAndNameContainingIgnoreCase(
                        user.organizationId(), user.userId(), term)
                .stream()
                .map(MediaFolder::getId)
                .toList();
        return new SearchScope(term, folderIds);
    }

    private Sort sortFor(String sortOrder) {
        String normalized = sortOrder == null || sortOrder.isBlank()
                ? "NEWEST"
                : sortOrder.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "OLDEST" -> Sort.by(Sort.Direction.ASC, "createdAt");
            case "NAME_ASC" -> Sort.by(Sort.Direction.ASC, "fileName");
            case "NAME_DESC" -> Sort.by(Sort.Direction.DESC, "fileName");
            case "SIZE_DESC" -> Sort.by(Sort.Direction.DESC, "fileSize");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }

    private Map<Long, Long> folderCounts(CurrentUser user, List<MediaFolder> folders) {
        Map<Long, Long> counts = new HashMap<>();
        if (folders.isEmpty()) {
            return counts;
        }
        List<Long> folderIds = folders.stream().map(MediaFolder::getId).toList();
        for (Object[] row : mediaRepository.countByFolderIds(user.organizationId(), user.userId(), folderIds)) {
            if (row[0] instanceof Long folderId && row[1] instanceof Number count) {
                counts.put(folderId, count.longValue());
            }
        }
        return counts;
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

    private ImportedBinaryMedia downloadExternalMedia(String sourceUrl, MediaType expectedType) {
        URI uri = parsePublicUri(sourceUrl);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(EXTERNAL_MEDIA_TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 400) {
                throw new BusinessException(
                        "Media URL is broken, private, or inaccessible from the server (HTTP "
                                + response.statusCode() + ").");
            }
            String contentType = response.headers().firstValue("content-type")
                    .map(value -> value.split(";")[0].trim().toLowerCase(Locale.ROOT))
                    .orElseThrow(() -> new BusinessException("Media URL did not return a content type."));
            MediaType actualType = mediaTypeFromContentType(contentType)
                    .orElseThrow(() -> new BusinessException("Unsupported media type at " + sourceUrl));
            if (expectedType != null && expectedType != actualType) {
                throw new BusinessException("Media URL type does not match the provided column.");
            }
            byte[] body = response.body() == null ? new byte[0] : response.body();
            if (body.length == 0) {
                throw new BusinessException("Media URL returned an empty file.");
            }
            String fileName = filenameFromUri(uri, contentType, actualType);
            return new ImportedBinaryMedia(
                    fileName,
                    fileName,
                    resolveExtension(fileName, contentType, actualType),
                    contentType,
                    actualType,
                    (long) body.length,
                    checksum(body),
                    body);
        } catch (IOException ex) {
            throw new BusinessException("Could not download media from " + sourceUrl + ".");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Media download was interrupted for " + sourceUrl + ".");
        }
    }

    private ImportedBinaryMedia downloadDriveMedia(String driveUrl, String fileId, DriveFile driveFile) {
        String mimeType = Optional.ofNullable(driveFile.mimeType())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new BusinessException("Google Drive file has no media type."));
        MediaType mediaType = mediaTypeFromContentType(mimeType)
                .orElseThrow(() -> new BusinessException("Unsupported Google Drive media type: " + mimeType));
        DownloadedFile downloaded = googleDriveService.downloadMediaFile(fileId);
        byte[] body = downloaded.body() == null ? new byte[0] : downloaded.body();
        if (body.length == 0) {
            throw new BusinessException("Google Drive file is empty or inaccessible: " + driveUrl);
        }
        String fileName = filenameFromDrive(driveFile.name(), mimeType, mediaType);
        long size = parseLong(driveFile.size()).orElse((long) body.length);
        return new ImportedBinaryMedia(
                fileName,
                fileName,
                resolveExtension(fileName, mimeType, mediaType),
                mimeType,
                mediaType,
                size,
                checksum(body),
                body);
    }

    private URI parsePublicUri(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
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

    private Optional<MediaType> mediaTypeFromContentType(String contentType) {
        String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "image/jpeg", "image/png", "image/webp", "image/gif" -> Optional.of(MediaType.IMAGE);
            case "video/mp4", "video/quicktime", "video/x-msvideo", "video/avi", "video/webm" ->
                    Optional.of(MediaType.VIDEO);
            default -> Optional.empty();
        };
    }

    private String filenameFromUri(URI uri, String contentType, MediaType mediaType) {
        String path = Optional.ofNullable(uri.getPath()).orElse("");
        String candidate = path.substring(path.lastIndexOf('/') + 1).trim();
        if (candidate.isBlank()) {
            return filenameFromDrive("imported-media", contentType, mediaType);
        }
        if (candidate.contains("?")) {
            candidate = candidate.substring(0, candidate.indexOf('?'));
        }
        String extension = resolveExtension(candidate, contentType, mediaType);
        return ensureExtension(candidate.isBlank() ? "imported-media" : candidate, extension);
    }

    private String filenameFromDrive(String driveName, String contentType, MediaType mediaType) {
        String base = driveName == null || driveName.isBlank() ? "google-drive-media" : driveName.trim();
        String extension = resolveExtension(base, contentType, mediaType);
        return ensureExtension(base, extension);
    }

    private String ensureExtension(String fileName, String extension) {
        if (fileName.toLowerCase(Locale.ROOT).endsWith("." + extension.toLowerCase(Locale.ROOT))) {
            return fileName;
        }
        return fileName + "." + extension;
    }

    private String extractGoogleDriveFileId(String googleDriveUrl) {
        URI uri;
        try {
            uri = URI.create(googleDriveUrl.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Google Drive URL is not a valid URL.");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!host.contains("drive.google.com")) {
            throw new BusinessException("Google Drive URL must point to drive.google.com.");
        }
        List<String> pathSegments = UriComponentsBuilder.fromUri(uri).build().getPathSegments();
        for (int i = 0; i < pathSegments.size() - 1; i++) {
            if ("d".equals(pathSegments.get(i)) && !pathSegments.get(i + 1).isBlank()) {
                return pathSegments.get(i + 1);
            }
        }
        String query = uri.getQuery();
        if (query != null) {
            for (String pair : query.split("&")) {
                int idx = pair.indexOf('=');
                if (idx > 0 && "id".equals(pair.substring(0, idx)) && idx < pair.length() - 1) {
                    return pair.substring(idx + 1);
                }
            }
        }
        throw new BusinessException("Could not extract a Google Drive file ID from the provided URL.");
    }

    private Optional<Long> parseLong(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private record FileInfo(
            String fileName,
            String extension,
            String contentType,
            MediaType mediaType,
            Long size,
            String checksum) {}

    private record ImportedBinaryMedia(
            String fileName,
            String originalFileName,
            String extension,
            String contentType,
            MediaType mediaType,
            Long fileSize,
            String checksum,
            byte[] bytes) {}

    private record SearchScope(String term, List<Long> folderIds) {
        private static SearchScope empty() {
            return new SearchScope("", List.of());
        }

        private boolean hasSearch() {
            return term != null && !term.isBlank();
        }
    }

    public record DownloadedMedia(String fileName, String contentType, byte[] body) {}
}
