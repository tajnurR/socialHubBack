package com.socialhub.socialhubBackend.media.web;

import com.socialhub.socialhubBackend.common.response.ApiResponse;
import com.socialhub.socialhubBackend.media.dto.MediaDtos.CreateMediaFolderRequest;
import com.socialhub.socialhubBackend.media.dto.MediaDtos.MediaBulkUploadResult;
import com.socialhub.socialhubBackend.media.dto.MediaDtos.MediaFolderResponse;
import com.socialhub.socialhubBackend.media.dto.MediaDtos.MediaItemResponse;
import com.socialhub.socialhubBackend.media.dto.MediaDtos.MediaPageResponse;
import com.socialhub.socialhubBackend.media.service.MediaService;
import com.socialhub.socialhubBackend.media.service.MediaService.DownloadedMedia;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/media")
@Tag(name = "Media Library", description = "Google Drive-backed media library")
public class MediaController {

    private final MediaService service;

    public MediaController(MediaService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List the current user's media library")
    public ApiResponse<List<MediaItemResponse>> list(
            @RequestParam(defaultValue = "ALL") String filter,
            @RequestParam(required = false) Long folderId) {
        return ApiResponse.ok(service.list(filter, folderId));
    }

    @GetMapping("/page")
    @Operation(summary = "Page through the current user's media library")
    public ApiResponse<MediaPageResponse> page(
            @RequestParam(defaultValue = "ALL") String filter,
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "NEWEST") String sortOrder,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.page(filter, folderId, search, sortOrder, page, size));
    }

    @GetMapping("/folders")
    @Operation(summary = "List the current user's media folders")
    public ApiResponse<List<MediaFolderResponse>> folders() {
        return ApiResponse.ok(service.folders());
    }

    @PostMapping("/folders")
    @Operation(summary = "Create a media folder and matching Google Drive folder")
    public ApiResponse<MediaFolderResponse> createFolder(@Valid @RequestBody CreateMediaFolderRequest request) {
        return ApiResponse.ok(service.createFolder(request), "Media folder created");
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload one or more media files to Google Drive and save metadata")
    public ApiResponse<MediaBulkUploadResult> upload(
            @RequestPart("files") MultipartFile[] files,
            @RequestParam(required = false) Long folderId) {
        return ApiResponse.ok(service.upload(files, folderId), "Media upload processed");
    }

    @PostMapping(value = "/{id}/retry", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Retry a failed upload by re-selecting the original file")
    public ApiResponse<MediaItemResponse> retry(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(service.retry(id, file), "Media upload retried");
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Download a media file from the connected Google Drive account")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        DownloadedMedia media = service.download(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(media.fileName()))
                .contentType(MediaType.parseMediaType(media.contentType()))
                .body(media.body());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a media item and its Google Drive file")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null, "Media deleted");
    }

    @GetMapping("/export")
    @Operation(summary = "Export uploaded media URLs as CSV or XLSX")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) List<Long> ids) {
        boolean xlsx = "xlsx".equalsIgnoreCase(format);
        byte[] body = service.export(xlsx ? "xlsx" : "csv", folderId, ids);
        String filename = xlsx ? "media-library.xlsx" : "media-library.csv";
        MediaType contentType = xlsx
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : new MediaType("text", "csv", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(filename))
                .contentType(contentType)
                .body(body);
    }

    private String contentDisposition(String filename) {
        return ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString();
    }
}
