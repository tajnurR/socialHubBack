package com.socialhub.socialhubBackend.post.service;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.common.exception.ResourceNotFoundException;
import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import com.socialhub.socialhubBackend.integration.core.domain.SocialIntegration;
import com.socialhub.socialhubBackend.integration.core.repository.SocialIntegrationRepository;
import com.socialhub.socialhubBackend.post.domain.Post;
import com.socialhub.socialhubBackend.post.domain.PostStatus;
import com.socialhub.socialhubBackend.post.dto.PostDtos.BulkUploadResult;
import com.socialhub.socialhubBackend.post.dto.PostDtos.PostResponse;
import com.socialhub.socialhubBackend.post.dto.PostDtos.RowError;
import com.socialhub.socialhubBackend.post.dto.PostDtos.UpdatePostRequest;
import com.socialhub.socialhubBackend.post.repository.PostRepository;
import com.socialhub.socialhubBackend.post.service.PostExcelService.RawRow;
import com.socialhub.socialhubBackend.product.repository.ProductRepository;
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
    private final SocialIntegrationRepository integrationRepository;
    private final PostExcelService excelService;
    private final PostPublisher postPublisher;
    private final PostMapper postMapper;
    private final CurrentUserProvider currentUserProvider;

    public PostService(
            PostRepository postRepository,
            ProductRepository productRepository,
            SocialIntegrationRepository integrationRepository,
            PostExcelService excelService,
            PostPublisher postPublisher,
            PostMapper postMapper,
            CurrentUserProvider currentUserProvider) {
        this.postRepository = postRepository;
        this.productRepository = productRepository;
        this.integrationRepository = integrationRepository;
        this.excelService = excelService;
        this.postPublisher = postPublisher;
        this.postMapper = postMapper;
        this.currentUserProvider = currentUserProvider;
    }

    public byte[] template() {
        return excelService.generateTemplate();
    }

    public List<PostResponse> list(PostStatus status, Long pageId, Long productId) {
        CurrentUser user = currentUserProvider.currentUser();
        return postRepository
                .search(user.organizationId(), user.userId(), status, pageId, productId)
                .stream()
                .map(postMapper::toResponse)
                .toList();
    }

    public PostResponse get(Long id) {
        return postMapper.toResponse(getOwned(id));
    }

    @Transactional
    public BulkUploadResult bulkUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("No file uploaded.");
        }
        CurrentUser user = currentUserProvider.currentUser();

        // The user's own Facebook pages (by Page ID) and products (by SKU) — isolation.
        Map<String, SocialIntegration> pagesByExternalId = integrationRepository
                .findByOrganizationIdAndUserId(user.organizationId(), user.userId())
                .stream()
                .filter(i -> i.getPlatform() == SocialPlatform.FACEBOOK)
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
                toImport.add(buildPost(row, user, pagesByExternalId, productIdBySku));
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
        if (request.content() != null) {
            post.setContent(request.content());
        }
        post.setLink(request.link());
        post.setMediaUrl(request.mediaUrl());
        if (request.productId() != null) {
            post.setProductId(productRepository
                    .findByIdAndOrganizationIdAndUserId(
                            request.productId(), post.getOrganizationId(), post.getUserId())
                    .orElseThrow(() -> new BusinessException("Unknown product"))
                    .getId());
        }
        if (request.socialIntegrationId() != null) {
            post.setSocialIntegrationId(requireOwnedPage(request.socialIntegrationId(), post).getId());
        }
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
            Map<String, SocialIntegration> pagesByExternalId,
            Map<String, Long> productIdBySku) {
        if (row.message().isBlank()) {
            throw new RowValidationException("message is required");
        }
        if (row.pageId().isBlank()) {
            throw new RowValidationException("pageId is required");
        }
        // pageId in the sheet may include trailing helper text in the example; take the first token.
        String pageId = row.pageId().split("\\s+")[0];
        SocialIntegration page = pagesByExternalId.get(pageId);
        if (page == null) {
            throw new RowValidationException("You have no connected Facebook page with id " + pageId);
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
        post.setSocialIntegrationId(page.getId());
        post.setPlatform(SocialPlatform.FACEBOOK);
        post.setContent(row.message());
        post.setLink(row.link().isBlank() ? null : row.link());
        post.setProductId(productId);
        post.setScheduledAt(scheduledAt); // stored as a suggestion; status stays DRAFT until scheduled
        post.setStatus(PostStatus.DRAFT);
        return post;
    }

    private SocialIntegration requireOwnedPage(Long integrationId, Post post) {
        return integrationRepository
                .findByIdAndOrganizationIdAndUserId(integrationId, post.getOrganizationId(), post.getUserId())
                .orElseThrow(() -> new BusinessException("That page is not connected or not yours."));
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
}
