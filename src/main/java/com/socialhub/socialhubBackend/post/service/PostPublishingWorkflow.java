package com.socialhub.socialhubBackend.post.service;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.common.exception.ResourceNotFoundException;
import com.socialhub.socialhubBackend.post.domain.Post;
import com.socialhub.socialhubBackend.post.domain.PostStatus;
import com.socialhub.socialhubBackend.post.repository.PostRepository;
import com.socialhub.socialhubBackend.post.service.PostPublisher.PublishAttempt;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostPublishingWorkflow {

    private static final int MAX_RETRIES = 3;
    private static final long BACKOFF_MINUTES = 10;

    private final PostRepository postRepository;
    private final PostPublisher postPublisher;

    public PostPublishingWorkflow(PostRepository postRepository, PostPublisher postPublisher) {
        this.postRepository = postRepository;
        this.postPublisher = postPublisher;
    }

    @Transactional
    public List<Long> claimDuePostIds(Instant now, int batchSize) {
        List<Post> due = postRepository.findDueForUpdate(now, PageRequest.of(0, batchSize));
        due.forEach(post -> post.setStatus(PostStatus.PROCESSING));
        postRepository.saveAllAndFlush(due);
        return due.stream().map(Post::getId).toList();
    }

    @Transactional
    public Post processClaimedPost(Long postId) {
        Post post = postRepository.findByIdForUpdate(postId).orElseThrow(() -> new ResourceNotFoundException("Post", postId));
        if (post.getStatus() != PostStatus.PROCESSING) {
            return post;
        }
        applyAttempt(post, postPublisher.publish(post), true, false);
        return postRepository.save(post);
    }

    @Transactional
    public Post publishNow(Long postId, Long organizationId, Long userId) {
        Post post = postRepository
                .findByIdAndOrganizationIdAndUserIdForUpdate(postId, organizationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", postId));
        if (post.getStatus() == PostStatus.POSTED) {
            throw new BusinessException("This post is already published.");
        }
        if (post.getStatus() == PostStatus.PROCESSING) {
            throw new BusinessException("This post is already being published.", HttpStatus.CONFLICT);
        }
        post.setStatus(PostStatus.PROCESSING);
        postRepository.saveAndFlush(post);
        applyAttempt(post, postPublisher.publish(post), false, false);
        return postRepository.save(post);
    }

    @Transactional
    public Post retryNow(Long postId, Long organizationId, Long userId) {
        Post post = postRepository
                .findByIdAndOrganizationIdAndUserIdForUpdate(postId, organizationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", postId));
        if (post.getStatus() != PostStatus.FAILED) {
            throw new BusinessException("Only failed posts can be retried.");
        }
        post.setStatus(PostStatus.PROCESSING);
        post.setScheduledAt(Instant.now());
        post.setLastRetryAt(Instant.now());
        post.setRetryCount(post.getRetryCount() + 1);
        postRepository.saveAndFlush(post);
        applyAttempt(post, postPublisher.publish(post), false, true);
        return postRepository.save(post);
    }

    private void applyAttempt(Post post, PublishAttempt attempt, boolean allowAutoRetry, boolean manualRetry) {
        if (attempt.successful()) {
            post.setStatus(PostStatus.POSTED);
            post.setExternalPostId(attempt.externalPostId());
            post.setPublishedAt(attempt.publishedAt());
            post.setPublishResponseSummary(attempt.responseSummary());
            post.setErrorMessage(null);
            return;
        }

        post.setPublishedAt(null);
        post.setExternalPostId(null);
        post.setPublishResponseSummary(null);
        post.setErrorMessage(attempt.failureReason());

        if (allowAutoRetry && attempt.retryable() && post.getRetryCount() < MAX_RETRIES) {
            int attemptNumber = post.getRetryCount() + 1;
            Instant retryAt = Instant.now().plus(BACKOFF_MINUTES * attemptNumber, ChronoUnit.MINUTES);
            post.setRetryCount(attemptNumber);
            post.setLastRetryAt(Instant.now());
            post.setScheduledAt(retryAt);
            post.setStatus(PostStatus.PENDING);
            return;
        }

        if (manualRetry) {
            post.setLastRetryAt(Instant.now());
        }
        post.setStatus(PostStatus.FAILED);
    }
}
