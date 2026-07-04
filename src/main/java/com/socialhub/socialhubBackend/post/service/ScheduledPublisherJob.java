package com.socialhub.socialhubBackend.post.service;

import com.socialhub.socialhubBackend.post.domain.Post;
import com.socialhub.socialhubBackend.post.domain.PostStatus;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically publishes due scheduled posts.
 *
 * <p><b>Restart-safe:</b> state lives in the DB (status + scheduled_at); a crash
 * leaves posts in PENDING/PROCESSING state for safe recovery. <b>Multi-instance-safe:</b>
 * the due query claims rows with {@code FOR UPDATE SKIP LOCKED}, so concurrent
 * instances process disjoint batches. Temporary failures back off and retry later.
 */
@Component
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true")
public class ScheduledPublisherJob {

    private static final Logger log = LoggerFactory.getLogger(ScheduledPublisherJob.class);
    private static final int BATCH_SIZE = 25;

    private final PostPublishingWorkflow workflow;

    public ScheduledPublisherJob(PostPublishingWorkflow workflow) {
        this.workflow = workflow;
    }

    @Scheduled(
            fixedDelayString = "${app.scheduler.poll-interval-ms:60000}",
            initialDelayString = "${app.scheduler.initial-delay-ms:20000}")
    public void publishDuePosts() {
        List<Long> due = workflow.claimDuePostIds(Instant.now(), BATCH_SIZE);
        if (due.isEmpty()) {
            return;
        }
        log.info("Scheduler: publishing {} due post(s)", due.size());
        for (Long postId : due) {
            Post post = workflow.processClaimedPost(postId);
            if (post.getStatus() == PostStatus.PENDING) {
                log.info("Post {} failed temporarily; retry {} scheduled for {}",
                        post.getId(), post.getRetryCount(), post.getScheduledAt());
            }
        }
    }
}
