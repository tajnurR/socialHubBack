package com.socialhub.socialhubBackend.post.service;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.common.exception.ResourceNotFoundException;
import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import com.socialhub.socialhubBackend.integration.core.domain.SocialIntegration;
import com.socialhub.socialhubBackend.integration.core.repository.SocialIntegrationRepository;
import com.socialhub.socialhubBackend.media.domain.MediaAsset;
import com.socialhub.socialhubBackend.media.domain.MediaType;
import com.socialhub.socialhubBackend.media.dto.MediaDtos.MediaItemResponse;
import com.socialhub.socialhubBackend.media.repository.MediaAssetRepository;
import com.socialhub.socialhubBackend.media.service.MediaService;
import com.socialhub.socialhubBackend.post.domain.Post;
import com.socialhub.socialhubBackend.post.domain.PostMediaType;
import com.socialhub.socialhubBackend.post.domain.PostMediaAsset;
import com.socialhub.socialhubBackend.post.domain.PostStatus;
import com.socialhub.socialhubBackend.post.dto.PostDtos.BulkUploadResult;
import com.socialhub.socialhubBackend.post.dto.PostDtos.CreatePostRequest;
import com.socialhub.socialhubBackend.post.dto.PostDtos.PostResponse;
import com.socialhub.socialhubBackend.post.dto.PostDtos.RowError;
import com.socialhub.socialhubBackend.post.dto.PostDtos.UpdatePostRequest;
import com.socialhub.socialhubBackend.post.repository.PostRepository;
import com.socialhub.socialhubBackend.post.repository.PostMediaAssetRepository;
import com.socialhub.socialhubBackend.post.service.PostExcelService.RawRow;
import com.socialhub.socialhubBackend.product.domain.Product;
import com.socialhub.socialhubBackend.product.repository.ProductRepository;
import com.socialhub.socialhubBackend.schedule.repository.ScheduleEventRepository;
import com.socialhub.socialhubBackend.user.context.CurrentUser;
import com.socialhub.socialhubBackend.user.context.CurrentUserProvider;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** User-scoped post management: bulk import, CRUD on drafts, and publish-now. */
@Service
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final ProductRepository productRepository;
    private final ScheduleEventRepository scheduleEventRepository;
    private final SocialIntegrationRepository integrationRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final PostMediaAssetRepository postMediaAssetRepository;
    private final MediaService mediaService;
    private final PostExcelService excelService;
    private final MediaUrlValidator mediaUrlValidator;
    private final PostPublishingWorkflow publishingWorkflow;
    private final PostMapper postMapper;
    private final CurrentUserProvider currentUserProvider;

    public PostService(
            PostRepository postRepository,
            ProductRepository productRepository,
            ScheduleEventRepository scheduleEventRepository,
            SocialIntegrationRepository integrationRepository,
            MediaAssetRepository mediaAssetRepository,
            PostMediaAssetRepository postMediaAssetRepository,
            MediaService mediaService,
            PostExcelService excelService,
            MediaUrlValidator mediaUrlValidator,
            PostPublishingWorkflow publishingWorkflow,
            PostMapper postMapper,
            CurrentUserProvider currentUserProvider) {
        this.postRepository = postRepository;
        this.productRepository = productRepository;
        this.scheduleEventRepository = scheduleEventRepository;
        this.integrationRepository = integrationRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.postMediaAssetRepository = postMediaAssetRepository;
        this.mediaService = mediaService;
        this.excelService = excelService;
        this.mediaUrlValidator = mediaUrlValidator;
        this.publishingWorkflow = publishingWorkflow;
        this.postMapper = postMapper;
        this.currentUserProvider = currentUserProvider;
    }

    public byte[] template(SocialPlatform platform, String format) {
        return excelService.generateTemplate(platform == null ? SocialPlatform.FACEBOOK : platform, format);
    }

    public List<PostResponse> list(
            String keyword,
            PostStatus status,
            SocialPlatform platform,
            Long pageId,
            Long productId,
            Long scheduleId,
            Instant from,
            Instant to) {
        CurrentUser user = currentUserProvider.currentUser();
        return postRepository
                .findAll(
                        postSpecification(
                        user.organizationId(),
                        user.userId(),
                        keyword,
                        status,
                        platform,
                        pageId,
                        productId,
                        scheduleId,
                        from,
                        to),
                        Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(postMapper::toResponse)
                .toList();
    }

    public PostResponse get(Long id) {
        return postMapper.toResponse(getOwned(id));
    }

    @Transactional
    public PostResponse create(CreatePostRequest request) {
        CurrentUser user = currentUserProvider.currentUser();
        SocialPlatform platform = requirePlatform(request.platform());
        Post post = new Post();
        post.setOrganizationId(user.organizationId());
        post.setUserId(user.userId());
        post.setPlatform(platform);
        applyEditable(post, request.title(), request.content(), request.link(), request.mediaUrl(), request.mediaAssetId(),
                request.mediaAssetIds(), request.productId(), request.socialIntegrationId(), null,
                PostStatus.DRAFT, null, platform);
        Post saved = postRepository.save(post);
        syncMediaLinks(saved, mediaIds(request.mediaAssetId(), request.mediaAssetIds()));
        return postMapper.toResponse(saved);
    }

    @Transactional
    public BulkUploadResult bulkUpload(SocialPlatform platform, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("No file uploaded.");
        }
        SocialPlatform selectedPlatform = requirePlatform(platform);
        CurrentUser user = currentUserProvider.currentUser();

        // The user's own platform accounts/pages and products — all matched inside the owner scope.
        Map<String, SocialIntegration> accountsByIdentifier = new HashMap<>();
        integrationRepository
                .findByOrganizationIdAndUserId(user.organizationId(), user.userId())
                .stream()
                .filter(i -> i.getPlatform() == selectedPlatform)
                .forEach(i -> {
                    putIdentifier(accountsByIdentifier, i.getExternalAccountId(), i);
                    putIdentifier(accountsByIdentifier, i.getDisplayName(), i);
                });
        List<Product> products = productRepository
                .findByOrganizationIdAndUserIdOrderByNameAsc(user.organizationId(), user.userId());
        Map<String, Long> productIdBySku = products
                .stream()
                .filter(p -> p.getSku() != null && !p.getSku().isBlank())
                .collect(Collectors.toMap(
                        p -> normalizeKey(p.getSku()), Product::getId, (a, b) -> a));
        Map<String, Long> productIdByName = products
                .stream()
                .collect(Collectors.toMap(
                        p -> normalizeKey(p.getName()), Product::getId, (a, b) -> a));

        List<RawRow> rows;
        try (InputStream in = file.getInputStream()) {
            rows = excelService.parse(in, file.getOriginalFilename());
        } catch (IOException ex) {
            throw new BusinessException("Could not read the uploaded file.");
        }

        int importedCount = 0;
        List<RowError> errors = new ArrayList<>();
        for (RawRow row : rows) {
            try {
                buildPost(row, user, selectedPlatform, accountsByIdentifier, productIdByName, productIdBySku);
                importedCount++;
            } catch (RowValidationException ex) {
                errors.add(rowError(row, ex.getMessage()));
            }
        }
        String errorReportCsv = errors.isEmpty() ? null : errorReportCsv(errors);
        return new BulkUploadResult(
                importedCount,
                errors,
                errorReportCsv,
                errors.isEmpty() ? null : "bulk-upload-errors-" + Instant.now().toEpochMilli() + ".csv");
    }

    @Transactional
    public PostResponse update(Long id, UpdatePostRequest request) {
        Post post = getOwned(id);
        if (post.getStatus() == PostStatus.POSTED) {
            throw new BusinessException("A published post can't be edited.");
        }
        if (post.getStatus() == PostStatus.PROCESSING) {
            throw new BusinessException("A post being published can't be edited.");
        }
        SocialPlatform platform = request.platform() == null ? post.getPlatform() : request.platform();
        post.setPlatform(platform);
        applyEditable(post, request.title(), request.content(), request.link(), request.mediaUrl(), request.mediaAssetId(),
                request.mediaAssetIds(), request.productId(), request.socialIntegrationId(), post.getScheduleEventId(),
                post.getStatus(), post.getScheduledAt(), platform);
        Post saved = postRepository.save(post);
        syncMediaLinks(saved, mediaIds(request.mediaAssetId(), request.mediaAssetIds()));
        return postMapper.toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        postRepository.delete(getOwned(id));
    }

    @Transactional
    public PostResponse publishNow(Long id) {
        CurrentUser user = currentUserProvider.currentUser();
        Post post = publishingWorkflow.publishNow(id, user.organizationId(), user.userId());
        if (post.getStatus() == PostStatus.FAILED) {
            throw new BusinessException(post.getErrorMessage(), HttpStatus.BAD_GATEWAY);
        }
        return postMapper.toResponse(post);
    }

    @Transactional
    public PostResponse retryNow(Long id) {
        CurrentUser user = currentUserProvider.currentUser();
        return postMapper.toResponse(publishingWorkflow.retryNow(id, user.organizationId(), user.userId()));
    }

    @Transactional
    public PostResponse clonePosted(Long id) {
        Post source = getOwned(id);
        if (source.getStatus() != PostStatus.POSTED) {
            throw new BusinessException("Only posted posts can be cloned.");
        }
        Post clone = new Post();
        clone.setOrganizationId(source.getOrganizationId());
        clone.setUserId(source.getUserId());
        clone.setSocialIntegrationId(source.getSocialIntegrationId());
        clone.setPlatform(source.getPlatform());
        clone.setContent(source.getContent());
        clone.setTitle(source.getTitle());
        clone.setLink(source.getLink());
        clone.setMediaUrl(source.getMediaUrl());
        clone.setMediaAssetId(source.getMediaAssetId());
        clone.setMediaType(source.getMediaType());
        clone.setHashtags(source.getHashtags());
        clone.setCta(source.getCta());
        clone.setProductId(source.getProductId());
        clone.setStatus(PostStatus.DRAFT);
        clone.setScheduledAt(null);
        clone.setTimeOverride(null);
        clone.setScheduledAtOverride(null);
        clone.setScheduleEventId(null);
        clone.setSortOrder(0);
        clone.setExternalPostId(null);
        clone.setPublishedAt(null);
        clone.setPublishResponseSummary(null);
        clone.setErrorMessage(null);
        clone.setRetryCount(0);
        clone.setLastRetryAt(null);
        Post saved = postRepository.save(clone);
        syncMediaLinks(saved, existingMediaIds(source));
        return postMapper.toResponse(saved);
    }

    /** Ownership-checked fetch (404 if not the current user's). */
    public Post getOwned(Long id) {
        CurrentUser user = currentUserProvider.currentUser();
        return postRepository
                .findByIdAndOrganizationIdAndUserId(id, user.organizationId(), user.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Post", id));
    }

    private Post buildPost(
            RawRow row,
            CurrentUser user,
            SocialPlatform platform,
            Map<String, SocialIntegration> accountsByIdentifier,
            Map<String, Long> productIdByName,
            Map<String, Long> productIdBySku) {
        if (row.postContent().isBlank()) {
            throw new RowValidationException("postContent is required");
        }
        if (row.postTitle().isBlank()) {
            throw new RowValidationException("postTitle is required");
        }
        if (row.product().isBlank()) {
            throw new RowValidationException("product is required");
        }
        if (row.pageId().isBlank()) {
            throw new RowValidationException("pageId is required");
        }
        // pageId in the sheet may include trailing helper text in the example; take the first token.
        String accountId = row.pageId().split("\\s+")[0];
        SocialIntegration account = accountsByIdentifier.get(normalizeKey(accountId));
        if (account == null) {
            account = accountsByIdentifier.get(normalizeKey(row.pageId()));
        }
        if (account == null) {
            throw new RowValidationException(
                    "You have no connected " + platform.name() + " account/page matching " + row.pageId());
        }

        Long productId = productIdByName.get(normalizeKey(row.product()));
        if (productId == null) {
            productId = productIdBySku.get(normalizeKey(row.product()));
        }
        if (!row.productSku().isBlank()) {
            String sku = row.productSku().split("\\s+")[0].toLowerCase();
            productId = productIdBySku.get(sku);
            if (productId == null) {
                throw new RowValidationException("Unknown product SKU: " + sku);
            }
        }
        if (productId == null) {
            throw new RowValidationException("Unknown product: " + row.product());
        }

        List<AppliedMedia> mediaItems = resolveBulkMedia(row);
        AppliedMedia primaryMedia = mediaItems.isEmpty() ? new AppliedMedia(null, null, null) : mediaItems.get(0);

        Post post = new Post();
        post.setOrganizationId(user.organizationId());
        post.setUserId(user.userId());
        post.setSocialIntegrationId(account.getId());
        post.setPlatform(platform);
        post.setTitle(row.postTitle());
        post.setContent(row.postContent());
        post.setLink(row.link().isBlank() ? null : row.link());
        post.setMediaAssetId(primaryMedia.mediaAssetId());
        post.setMediaUrl(primaryMedia.mediaUrl());
        post.setMediaType(primaryMedia.mediaType());
        post.setProductId(productId);
        post.setScheduledAt(null);
        post.setStatus(PostStatus.DRAFT);
        Post saved = postRepository.save(post);
        syncMediaLinks(saved, mediaItems.stream().map(AppliedMedia::mediaAssetId).filter(id -> id != null).toList());
        return saved;
    }

    private void applyEditable(
            Post post,
            String title,
            String content,
            String link,
            String mediaUrl,
            Long mediaAssetId,
            List<Long> mediaAssetIds,
            Long productId,
            Long socialIntegrationId,
            Long scheduleEventId,
            PostStatus requestedStatus,
            Instant scheduledAt,
            SocialPlatform platform) {
        post.setTitle(blankToNull(title));
        if (post.getTitle() == null) {
            throw new BusinessException("Post title is required.");
        }
        post.setContent(requiredContent(content));
        post.setLink(blankToNull(link));
        AppliedMedia appliedMedia = resolveMedia(post, primaryMediaAssetId(mediaAssetId, mediaAssetIds), mediaUrl);
        post.setMediaAssetId(appliedMedia.mediaAssetId());
        post.setMediaUrl(appliedMedia.mediaUrl());
        post.setMediaType(appliedMedia.mediaType());
        if (productId == null) {
            throw new BusinessException("Product is required.");
        }
        post.setProductId(resolveProductId(productId, post));
        post.setSocialIntegrationId(requireOwnedAccount(socialIntegrationId, post, platform).getId());
        post.setScheduleEventId(resolveScheduleId(scheduleEventId, post));
        post.setScheduledAt(scheduledAt);
        PostStatus status = requestedStatus == null ? PostStatus.DRAFT : requestedStatus;
        if (status == PostStatus.POSTED) {
            throw new BusinessException("Use publish-now to publish a post.");
        }
        if ((status == PostStatus.SCHEDULED || status == PostStatus.PENDING) && scheduledAt == null) {
            throw new BusinessException("Scheduled posts require a publish date and time.");
        }
        post.setStatus(status);
        if (status != PostStatus.FAILED) {
            post.setErrorMessage(null);
        }
        if (status == PostStatus.DRAFT) {
            post.setPublishResponseSummary(null);
            post.setPublishedAt(null);
            post.setExternalPostId(null);
        }
        if (status != PostStatus.PENDING && status != PostStatus.PROCESSING) {
            post.setRetryCount(0);
            post.setLastRetryAt(null);
        }
    }

    private Long resolveProductId(Long productId, Post post) {
        if (productId == null) {
            return null;
        }
        return productRepository
                .findByIdAndOrganizationIdAndUserId(productId, post.getOrganizationId(), post.getUserId())
                .orElseThrow(() -> new BusinessException("Unknown product"))
                .getId();
    }

    private Long resolveScheduleId(Long scheduleEventId, Post post) {
        if (scheduleEventId == null) {
            return null;
        }
        return scheduleEventRepository
                .findByIdAndOrganizationIdAndUserId(
                        scheduleEventId, post.getOrganizationId(), post.getUserId())
                .orElseThrow(() -> new BusinessException("Unknown schedule"))
                .getId();
    }

    private SocialIntegration requireOwnedAccount(Long integrationId, Post post, SocialPlatform platform) {
        if (integrationId == null) {
            throw new BusinessException("Select a target page/account.");
        }
        SocialIntegration integration = integrationRepository
                .findByIdAndOrganizationIdAndUserId(integrationId, post.getOrganizationId(), post.getUserId())
                .orElseThrow(() -> new BusinessException("That page is not connected or not yours."));
        if (integration.getPlatform() != platform) {
            throw new BusinessException("Selected account does not match the post platform.");
        }
        return integration;
    }

    private SocialPlatform requirePlatform(SocialPlatform platform) {
        if (platform == null) {
            throw new BusinessException("Select a social media platform.");
        }
        return platform;
    }

    private String requiredContent(String content) {
        String value = blankToNull(content);
        if (value == null) {
            throw new BusinessException("Post content is required.");
        }
        return value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private List<AppliedMedia> resolveBulkMedia(RawRow row) {
        List<AppliedMedia> mediaItems = new ArrayList<>();
        for (String googleDriveUrl : splitMediaReferences(row.googleDriveUrl())) {
            MediaItemResponse media = mediaService.attachGoogleDriveMedia(googleDriveUrl);
            ensureUploaded(media, "Google Drive media");
            mediaItems.add(toAppliedMedia(media));
        }
        for (String image : splitMediaReferences(row.imageUrl())) {
            PostMediaType type = validateMediaRow(image);
            if (type != PostMediaType.IMAGE) {
                throw new RowValidationException("imageUrl must contain supported image URLs only.");
            }
            MediaItemResponse media = mediaService.importExternalMedia(image, MediaType.IMAGE);
            ensureUploaded(media, "Imported image");
            mediaItems.add(toAppliedMedia(media));
        }
        for (String video : splitMediaReferences(row.videoUrl())) {
            PostMediaType type = validateMediaRow(video);
            if (type != PostMediaType.VIDEO) {
                throw new RowValidationException("videoUrl must contain supported video URLs only.");
            }
            MediaItemResponse media = mediaService.importExternalMedia(video, MediaType.VIDEO);
            ensureUploaded(media, "Imported video");
            mediaItems.add(toAppliedMedia(media));
        }
        return dedupeAppliedMedia(mediaItems);
    }

    private AppliedMedia resolveMedia(Post post, Long mediaAssetId, String mediaUrl) {
        if (mediaAssetId != null) {
            MediaAsset asset = mediaAssetRepository
                    .findByIdAndOrganizationIdAndUserId(mediaAssetId, post.getOrganizationId(), post.getUserId())
                    .orElseThrow(() -> new BusinessException("Selected media is not in your library."));
            return new AppliedMedia(asset.getId(), preferredMediaUrl(asset), toPostMediaType(asset.getMediaType()));
        }
        String resolvedMediaUrl = blankToNull(mediaUrl);
        return new AppliedMedia(null, resolvedMediaUrl, mediaUrlValidator.validate(resolvedMediaUrl));
    }

    private String preferredMediaUrl(MediaAsset asset) {
        if (asset.getDirectDownloadUrl() != null && !asset.getDirectDownloadUrl().isBlank()) {
            return asset.getDirectDownloadUrl();
        }
        if (asset.getGoogleDriveUrl() != null && !asset.getGoogleDriveUrl().isBlank()) {
            return asset.getGoogleDriveUrl();
        }
        return null;
    }

    private PostMediaType toPostMediaType(MediaType mediaType) {
        if (mediaType == null) {
            return null;
        }
        return switch (mediaType) {
            case IMAGE -> PostMediaType.IMAGE;
            case VIDEO -> PostMediaType.VIDEO;
        };
    }

    private PostMediaType validateMediaRow(String url) {
        try {
            return mediaUrlValidator.validate(url);
        } catch (BusinessException ex) {
            throw new RowValidationException(ex.getMessage());
        }
    }

    private void putIdentifier(Map<String, SocialIntegration> map, String value, SocialIntegration integration) {
        String key = normalizeKey(value);
        if (!key.isBlank()) {
            map.putIfAbsent(key, integration);
        }
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private record AppliedMedia(Long mediaAssetId, String mediaUrl, PostMediaType mediaType) {}

    private Long primaryMediaAssetId(Long mediaAssetId, List<Long> mediaAssetIds) {
        if (mediaAssetIds != null && !mediaAssetIds.isEmpty()) {
            return mediaAssetIds.get(0);
        }
        return mediaAssetId;
    }

    private List<Long> mediaIds(Long mediaAssetId, List<Long> mediaAssetIds) {
        List<Long> ids = new ArrayList<>();
        if (mediaAssetIds != null) {
            ids.addAll(mediaAssetIds.stream().filter(id -> id != null).toList());
        } else if (mediaAssetId != null) {
            ids.add(mediaAssetId);
        }
        return ids.stream().distinct().toList();
    }

    private List<Long> existingMediaIds(Post post) {
        List<Long> ids = postMediaAssetRepository.findByPostIdOrderByDisplayOrderAscIdAsc(post.getId())
                .stream()
                .map(PostMediaAsset::getMediaAssetId)
                .toList();
        if (!ids.isEmpty()) {
            return ids;
        }
        return post.getMediaAssetId() == null ? List.of() : List.of(post.getMediaAssetId());
    }

    private void syncMediaLinks(Post post, List<Long> mediaAssetIds) {
        postMediaAssetRepository.deleteByPostId(post.getId());
        int index = 0;
        for (Long mediaAssetId : mediaAssetIds.stream().distinct().toList()) {
            MediaAsset asset = mediaAssetRepository
                    .findByIdAndOrganizationIdAndUserId(mediaAssetId, post.getOrganizationId(), post.getUserId())
                    .orElseThrow(() -> new BusinessException("Selected media is not in your library."));
            PostMediaAsset link = new PostMediaAsset();
            link.setPostId(post.getId());
            link.setMediaAssetId(asset.getId());
            link.setDisplayOrder(index++);
            postMediaAssetRepository.save(link);
        }
    }

    private List<String> splitMediaReferences(String value) {
        String resolved = blankToNull(value);
        if (resolved == null) {
            return List.of();
        }
        return java.util.Arrays.stream(resolved.split("[\\n;,]+"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private List<AppliedMedia> dedupeAppliedMedia(List<AppliedMedia> items) {
        Map<Long, AppliedMedia> byId = new java.util.LinkedHashMap<>();
        for (AppliedMedia item : items) {
            if (item.mediaAssetId() != null) {
                byId.putIfAbsent(item.mediaAssetId(), item);
            }
        }
        return new ArrayList<>(byId.values());
    }

    private AppliedMedia toAppliedMedia(MediaItemResponse media) {
        PostMediaType mediaType = switch (media.mediaType()) {
            case IMAGE -> PostMediaType.IMAGE;
            case VIDEO -> PostMediaType.VIDEO;
        };
        String resolvedUrl = media.directDownloadUrl() != null && !media.directDownloadUrl().isBlank()
                ? media.directDownloadUrl()
                : media.googleDriveUrl();
        return new AppliedMedia(media.mediaId(), resolvedUrl, mediaType);
    }

    private void ensureUploaded(MediaItemResponse media, String label) {
        if (media.uploadStatus() != com.socialhub.socialhubBackend.media.domain.MediaUploadStatus.UPLOADED) {
            throw new RowValidationException(label + " is not uploaded and ready to attach.");
        }
    }

    private RowError rowError(RawRow row, String message) {
        return new RowError(
                row.rowNumber(),
                message,
                blankToNull(row.postTitle()),
                blankToNull(row.pageId()),
                firstMediaReference(row));
    }

    private String firstMediaReference(RawRow row) {
        return List.of(blankToNull(row.googleDriveUrl()), blankToNull(row.imageUrl()), blankToNull(row.videoUrl()))
                .stream()
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }

    private String errorReportCsv(List<RowError> errors) {
        StringBuilder out = new StringBuilder();
        out.append("row,message,postTitle,pageId,mediaReference\n");
        errors.stream()
                .sorted(Comparator.comparingInt(RowError::row))
                .forEach(error -> out.append(csv(error.row()))
                        .append(',').append(csv(error.message()))
                        .append(',').append(csv(error.postTitle()))
                        .append(',').append(csv(error.pageId()))
                        .append(',').append(csv(error.mediaReference()))
                        .append('\n'));
        return out.toString();
    }

    private String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    /** Internal: a single row failed validation (carried as a per-row error). */
    private static final class RowValidationException extends RuntimeException {
        RowValidationException(String message) {
            super(message);
        }
    }

    private Specification<Post> postSpecification(
            Long organizationId,
            Long userId,
            String keyword,
            PostStatus status,
            SocialPlatform platform,
            Long pageId,
            Long productId,
            Long scheduleId,
            Instant from,
            Instant to) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), organizationId));
            predicates.add(cb.equal(root.get("userId"), userId));
            String normalizedKeyword = blankToNull(keyword);
            if (normalizedKeyword != null) {
                String pattern = "%" + normalizedKeyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("content")), pattern)));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (platform != null) {
                predicates.add(cb.equal(root.get("platform"), platform));
            }
            if (pageId != null) {
                predicates.add(cb.equal(root.get("socialIntegrationId"), pageId));
            }
            if (productId != null) {
                predicates.add(cb.equal(root.get("productId"), productId));
            }
            if (scheduleId != null) {
                predicates.add(cb.equal(root.get("scheduleEventId"), scheduleId));
            }
            if (from != null) {
                predicates.add(cb.or(
                        cb.greaterThanOrEqualTo(root.get("createdAt"), from),
                        cb.greaterThanOrEqualTo(root.get("scheduledAt"), from)));
            }
            if (to != null) {
                predicates.add(cb.or(
                        cb.lessThanOrEqualTo(root.get("createdAt"), to),
                        cb.lessThanOrEqualTo(root.get("scheduledAt"), to)));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
