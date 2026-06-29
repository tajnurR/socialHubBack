package com.socialhub.socialhubBackend.post.service;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.common.exception.ResourceNotFoundException;
import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import com.socialhub.socialhubBackend.integration.core.domain.SocialIntegration;
import com.socialhub.socialhubBackend.integration.core.repository.SocialIntegrationRepository;
import com.socialhub.socialhubBackend.post.domain.Post;
import com.socialhub.socialhubBackend.post.domain.PostStatus;
import com.socialhub.socialhubBackend.post.dto.PostDtos.BulkUploadResult;
import com.socialhub.socialhubBackend.post.dto.PostDtos.CreatePostRequest;
import com.socialhub.socialhubBackend.post.dto.PostDtos.PostResponse;
import com.socialhub.socialhubBackend.post.dto.PostDtos.RowError;
import com.socialhub.socialhubBackend.post.dto.PostDtos.UpdatePostRequest;
import com.socialhub.socialhubBackend.post.repository.PostRepository;
import com.socialhub.socialhubBackend.post.service.PostExcelService.RawRow;
import com.socialhub.socialhubBackend.product.repository.ProductRepository;
import com.socialhub.socialhubBackend.schedule.repository.ScheduleEventRepository;
import com.socialhub.socialhubBackend.user.context.CurrentUser;
import com.socialhub.socialhubBackend.user.context.CurrentUserProvider;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
    private final PostExcelService excelService;
    private final PostPublisher postPublisher;
    private final PostMapper postMapper;
    private final CurrentUserProvider currentUserProvider;

    public PostService(
            PostRepository postRepository,
            ProductRepository productRepository,
            ScheduleEventRepository scheduleEventRepository,
            SocialIntegrationRepository integrationRepository,
            PostExcelService excelService,
            PostPublisher postPublisher,
            PostMapper postMapper,
            CurrentUserProvider currentUserProvider) {
        this.postRepository = postRepository;
        this.productRepository = productRepository;
        this.scheduleEventRepository = scheduleEventRepository;
        this.integrationRepository = integrationRepository;
        this.excelService = excelService;
        this.postPublisher = postPublisher;
        this.postMapper = postMapper;
        this.currentUserProvider = currentUserProvider;
    }

    public byte[] template(SocialPlatform platform) {
        return excelService.generateTemplate(platform == null ? SocialPlatform.FACEBOOK : platform);
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
        applyEditable(post, request.title(), request.content(), request.link(), request.mediaUrl(),
                request.productId(), request.socialIntegrationId(), request.scheduleEventId(),
                request.status(), request.scheduledAt(), platform);
        return postMapper.toResponse(postRepository.save(post));
    }

    @Transactional
    public BulkUploadResult bulkUpload(SocialPlatform platform, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("No file uploaded.");
        }
        SocialPlatform selectedPlatform = requirePlatform(platform);
        CurrentUser user = currentUserProvider.currentUser();

        // The user's own platform accounts/pages (by external id) and products (by SKU) — isolation.
        Map<String, SocialIntegration> accountsByExternalId = integrationRepository
                .findByOrganizationIdAndUserId(user.organizationId(), user.userId())
                .stream()
                .filter(i -> i.getPlatform() == selectedPlatform)
                .collect(Collectors.toMap(
                        SocialIntegration::getExternalAccountId, Function.identity(), (a, b) -> a));
        Map<String, Long> productIdBySku = productRepository
                .findByOrganizationIdAndUserIdOrderByNameAsc(user.organizationId(), user.userId())
                .stream()
                .filter(p -> p.getSku() != null && !p.getSku().isBlank())
                .collect(Collectors.toMap(
                        p -> p.getSku().trim().toLowerCase(), com.socialhub.socialhubBackend.product.domain.Product::getId, (a, b) -> a));

        List<RawRow> rows;
        try (InputStream in = file.getInputStream()) {
            rows = excelService.parse(in);
        } catch (IOException ex) {
            throw new BusinessException("Could not read the uploaded file.");
        }

        List<Post> toImport = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        for (RawRow row : rows) {
            try {
                toImport.add(buildPost(row, user, selectedPlatform, accountsByExternalId, productIdBySku));
            } catch (RowValidationException ex) {
                errors.add(new RowError(row.rowNumber(), ex.getMessage()));
            }
        }
        postRepository.saveAll(toImport);
        return new BulkUploadResult(toImport.size(), errors);
    }

    @Transactional
    public PostResponse update(Long id, UpdatePostRequest request) {
        Post post = getOwned(id);
        if (post.getStatus() == PostStatus.POSTED) {
            throw new BusinessException("A published post can't be edited.");
        }
        SocialPlatform platform = request.platform() == null ? post.getPlatform() : request.platform();
        post.setPlatform(platform);
        applyEditable(post, request.title(), request.content(), request.link(), request.mediaUrl(),
                request.productId(), request.socialIntegrationId(), request.scheduleEventId(),
                request.status(), request.scheduledAt(), platform);
        return postMapper.toResponse(postRepository.save(post));
    }

    @Transactional
    public void delete(Long id) {
        postRepository.delete(getOwned(id));
    }

    @Transactional
    public PostResponse publishNow(Long id) {
        Post post = getOwned(id);
        if (post.getStatus() == PostStatus.POSTED) {
            throw new BusinessException("This post is already published.");
        }
        if (post.getSocialIntegrationId() == null) {
            throw new BusinessException("Select a target page/account before publishing.");
        }
        postPublisher.publish(post);
        postRepository.save(post);
        if (post.getStatus() == PostStatus.FAILED) {
            throw new BusinessException(post.getErrorMessage(), HttpStatus.BAD_GATEWAY);
        }
        return postMapper.toResponse(post);
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
            Map<String, SocialIntegration> accountsByExternalId,
            Map<String, Long> productIdBySku) {
        if (row.message().isBlank()) {
            throw new RowValidationException("message is required");
        }
        if (row.pageId().isBlank()) {
            throw new RowValidationException("pageId is required");
        }
        // pageId in the sheet may include trailing helper text in the example; take the first token.
        String accountId = row.pageId().split("\\s+")[0];
        SocialIntegration account = accountsByExternalId.get(accountId);
        if (account == null) {
            throw new RowValidationException("You have no connected " + platform.name() + " account with id " + accountId);
        }

        Long productId = null;
        if (!row.productSku().isBlank()) {
            String sku = row.productSku().split("\\s+")[0].toLowerCase();
            productId = productIdBySku.get(sku);
            if (productId == null) {
                throw new RowValidationException("Unknown product SKU: " + sku);
            }
        }

        Instant scheduledAt = null;
        if (!row.scheduledAt().isBlank()) {
            scheduledAt = parseInstant(row.scheduledAt().split("\\s+")[0]);
        }

        Post post = new Post();
        post.setOrganizationId(user.organizationId());
        post.setUserId(user.userId());
        post.setSocialIntegrationId(account.getId());
        post.setPlatform(platform);
        post.setContent(row.message());
        post.setLink(row.link().isBlank() ? null : row.link());
        post.setProductId(productId);
        post.setScheduledAt(scheduledAt); // stored as a suggestion; status stays DRAFT until scheduled
        post.setStatus(PostStatus.DRAFT);
        return post;
    }

    private void applyEditable(
            Post post,
            String title,
            String content,
            String link,
            String mediaUrl,
            Long productId,
            Long socialIntegrationId,
            Long scheduleEventId,
            PostStatus requestedStatus,
            Instant scheduledAt,
            SocialPlatform platform) {
        post.setTitle(blankToNull(title));
        post.setContent(requiredContent(content));
        post.setLink(blankToNull(link));
        post.setMediaUrl(blankToNull(mediaUrl));
        post.setProductId(resolveProductId(productId, post));
        post.setSocialIntegrationId(requireOwnedAccount(socialIntegrationId, post, platform).getId());
        post.setScheduleEventId(resolveScheduleId(scheduleEventId, post));
        post.setScheduledAt(scheduledAt);
        PostStatus status = requestedStatus == null ? PostStatus.DRAFT : requestedStatus;
        if (status == PostStatus.POSTED) {
            throw new BusinessException("Use publish-now to publish a post.");
        }
        if (status == PostStatus.SCHEDULED && scheduledAt == null) {
            throw new BusinessException("Scheduled posts require a publish date and time.");
        }
        post.setStatus(status);
        if (status != PostStatus.FAILED) {
            post.setErrorMessage(null);
        }
        post.setRetryCount(0);
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

    private Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            try {
                return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
            } catch (RuntimeException ex) {
                throw new RowValidationException("Invalid scheduledAt (use e.g. 2026-07-01T09:00): " + value);
            }
        }
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
