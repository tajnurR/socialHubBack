package com.socialhub.socialhubBackend.post.web;

import com.socialhub.socialhubBackend.common.response.ApiResponse;
import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import com.socialhub.socialhubBackend.post.domain.PostStatus;
import com.socialhub.socialhubBackend.post.dto.PostDtos.BulkUploadResult;
import com.socialhub.socialhubBackend.post.dto.PostDtos.CreatePostRequest;
import com.socialhub.socialhubBackend.post.dto.PostDtos.PostResponse;
import com.socialhub.socialhubBackend.post.dto.PostDtos.UpdatePostRequest;
import com.socialhub.socialhubBackend.post.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Posts: bulk import, list/filter, CRUD on drafts, publish-now. User-scoped. */
@RestController
@RequestMapping("/api/v1/posts")
@Tag(name = "Posts", description = "Bulk create, manage, and publish posts")
public class PostController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping("/template")
    @Operation(summary = "Download the bulk-upload Excel template")
    public ResponseEntity<byte[]> template(
            @RequestParam(required = false, defaultValue = "FACEBOOK") SocialPlatform platform) {
        byte[] body = postService.template(platform);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(platform.name().toLowerCase() + "-posts-template.xlsx")
                                .build()
                                .toString())
                .contentType(MediaType.parseMediaType(XLSX))
                .body(body);
    }

    @PostMapping(path = "/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a filled template; imports valid rows as DRAFT posts")
    public ApiResponse<BulkUploadResult> bulkUpload(
            @RequestParam SocialPlatform platform,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(postService.bulkUpload(platform, file), "Bulk upload processed");
    }

    @GetMapping
    @Operation(summary = "List the user's posts with post-management filters")
    public ApiResponse<List<PostResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) PostStatus status,
            @RequestParam(required = false) SocialPlatform platform,
            @RequestParam(required = false) Long pageId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long scheduleId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return ApiResponse.ok(postService.list(keyword, status, platform, pageId, productId, scheduleId, from, to));
    }

    @GetMapping("/{id}")
    public ApiResponse<PostResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(postService.get(id));
    }

    @PostMapping
    @Operation(summary = "Create a single post")
    public ApiResponse<PostResponse> create(@Valid @RequestBody CreatePostRequest request) {
        return ApiResponse.ok(postService.create(request), "Post saved");
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit a draft post")
    public ApiResponse<PostResponse> update(
            @PathVariable Long id, @Valid @RequestBody UpdatePostRequest request) {
        return ApiResponse.ok(postService.update(id, request), "Post updated");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        postService.delete(id);
        return ApiResponse.ok(null, "Post deleted");
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish a post immediately")
    public ApiResponse<PostResponse> publish(@PathVariable Long id) {
        return ApiResponse.ok(postService.publishNow(id), "Post published");
    }
}
