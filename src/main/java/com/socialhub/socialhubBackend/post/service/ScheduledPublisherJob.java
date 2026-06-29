package com.socialhub.socialhubBackend.post.service;

import com.socialhub.socialhubBackend.post.domain.Post;
import com.socialhub.socialhubBackend.post.domain.PostStatus;
import com.socialhub.socialhubBackend.post.repository.PostRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodically publishes due scheduled posts.
 *
 * <p><b>Restart-safe:</b> state lives in the DB (status + scheduled_at); a crash
 * just leaves posts SCHEDULED to be retried. <b>Multi-instance-safe:</b> the due
 * query claims rows with {@code FOR UPDATE SKIP LOCKED}, so concurrent instances
 * process disjoint batches. Failures back off and retry up to a limit, then FAIL.
 */
@Component
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true")
public class ScheduledPublisherJob {

    private static final Logger log = LoggerFactory.getLogger(ScheduledPublisherJob.class);
    private static final int BATCH_SIZE = 25;
    private static final int MAX_RETRIES = 3;
    private static final long BACKOFF_MINUTES = 10;

    private final PostRepository postRepository;
    private final PostPublisher postPublisher;

    public ScheduledPublisherJob(PostRepository postRepository, PostPublisher postPublisher) {
        this.postRepository = postRepository;
        this.postPublisher = postPublisher;
    }

    @Scheduled(
            fixedDelayString = "${app.scheduler.poll-interval-ms:60000}",
            initialDelayString = "${app.scheduler.initial-delay-ms:20000}")
    @Transactional
    public void publishDuePosts() {
        List<Post> due = postRepository.findDueForUpdate(Instant.now(), PageRequest.of(0, BATCH_SIZE));
        if (due.isEmpty()) {
            return;
        }
        log.info("Scheduler: publishing {} due post(s)", due.size());
        for (Post post : due) {
            postPublisher.publish(post); // → POSTED or FAILED (+ error)
            if (post.getStatus() == PostStatus.FAILED && post.getRetryCount() < MAX_RETRIES) {
                int attempt = post.getRetryCount() + 1;
                post.setRetryCount(attempt);
                post.setStatus(PostStatus.SCHEDULED);
                post.setScheduledAt(Instant.now().plus(BACKOFF_MINUTES * attempt, ChronoUnit.MINUTES));
                log.info("Post {} failed; retry {} scheduled in {} min", post.getId(), attempt,
                        BACKOFF_MINUTES * attempt);
            }
            postRepository.save(post);
        }
    }
}
