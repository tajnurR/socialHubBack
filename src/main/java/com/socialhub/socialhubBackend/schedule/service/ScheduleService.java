package com.socialhub.socialhubBackend.schedule.service;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.common.exception.ResourceNotFoundException;
import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import com.socialhub.socialhubBackend.integration.core.repository.SocialIntegrationRepository;
import com.socialhub.socialhubBackend.post.domain.Post;
import com.socialhub.socialhubBackend.post.domain.PostStatus;
import com.socialhub.socialhubBackend.post.repository.PostRepository;
import com.socialhub.socialhubBackend.post.service.PostMapper;
import com.socialhub.socialhubBackend.schedule.domain.ScheduleEvent;
import com.socialhub.socialhubBackend.schedule.domain.ScheduleMode;
import com.socialhub.socialhubBackend.schedule.domain.ScheduleTemplate;
import com.socialhub.socialhubBackend.schedule.dto.ScheduleDtos.AttachPostsRequest;
import com.socialhub.socialhubBackend.schedule.dto.ScheduleDtos.BestTimeSuggestion;
import com.socialhub.socialhubBackend.schedule.dto.ScheduleDtos.ConflictWarning;
import com.socialhub.socialhubBackend.schedule.dto.ScheduleDtos.EngagementSummary;
import com.socialhub.socialhubBackend.schedule.dto.ScheduleDtos.QuickPostActionRequest;
import com.socialhub.socialhubBackend.schedule.dto.ScheduleDtos.ReschedulePostRequest;
import com.socialhub.socialhubBackend.schedule.dto.ScheduleDtos.ScheduleEventResponse;
import com.socialhub.socialhubBackend.schedule.dto.ScheduleDtos.ScheduleInsight;
import com.socialhub.socialhubBackend.schedule.dto.ScheduleDtos.ScheduleNotifications;
import com.socialhub.socialhubBackend.schedule.dto.ScheduleDtos.SchedulePostRequest;
import com.socialhub.socialhubBackend.schedule.dto.ScheduleDtos.SchedulePostResponse;
import com.socialhub.socialhubBackend.schedule.dto.ScheduleDtos.ScheduleRequest;
import com.socialhub.socialhubBackend.schedule.dto.ScheduleDtos.ScheduleResponse;
import com.socialhub.socialhubBackend.schedule.dto.ScheduleDtos.ScheduleTemplateRequest;
import com.socialhub.socialhubBackend.schedule.dto.ScheduleDtos.ScheduleTemplateResponse;
import com.socialhub.socialhubBackend.schedule.dto.ScheduleDtos.CreateScheduleEventRequest;
import com.socialhub.socialhubBackend.schedule.dto.ScheduleDtos.ScheduledPostInput;
import com.socialhub.socialhubBackend.schedule.repository.ScheduleEventRepository;
import com.socialhub.socialhubBackend.schedule.repository.ScheduleTemplateRepository;
import com.socialhub.socialhubBackend.user.context.CurrentUser;
import com.socialhub.socialhubBackend.user.context.CurrentUserProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ScheduleService {

    private static final Map<SocialPlatform, LocalTime> BEST_TIMES = Map.of(
            SocialPlatform.FACEBOOK, LocalTime.of(19, 30),
            SocialPlatform.INSTAGRAM, LocalTime.of(20, 0),
            SocialPlatform.TIKTOK, LocalTime.of(21, 0),
            SocialPlatform.YOUTUBE, LocalTime.of(18, 30),
            SocialPlatform.LINKEDIN, LocalTime.of(10, 30),
            SocialPlatform.X, LocalTime.of(12, 15),
            SocialPlatform.PINTEREST, LocalTime.of(20, 30));

    private final ScheduleEventRepository eventRepository;
    private final ScheduleTemplateRepository templateRepository;
    private final PostRepository postRepository;
    private final SocialIntegrationRepository integrationRepository;
    private final PostMapper postMapper;
    private final CurrentUserProvider currentUserProvider;

    public ScheduleService(
            ScheduleEventRepository eventRepository,
            ScheduleTemplateRepository templateRepository,
            PostRepository postRepository,
            SocialIntegrationRepository integrationRepository,
            PostMapper postMapper,
            CurrentUserProvider currentUserProvider) {
        this.eventRepository = eventRepository;
        this.templateRepository = templateRepository;
        this.postRepository = postRepository;
        this.integrationRepository = integrationRepository;
        this.postMapper = postMapper;
        this.currentUserProvider = currentUserProvider;
    }

    public List<ScheduleResponse> listSchedules() {
        CurrentUser user = currentUserProvider.currentUser();
        return eventRepository
                .findByOrganizationIdAndUserIdOrderByCreatedAtDesc(user.organizationId(), user.userId())
                .stream()
                .map(this::toScheduleResponse)
                .toList();
    }

    public ScheduleResponse getSchedule(Long id) {
        return toScheduleResponse(getOwnedEvent(id));
    }

    @Transactional
    public ScheduleResponse createSchedule(ScheduleRequest request) {
        ScheduleEvent event = new ScheduleEvent();
        CurrentUser user = currentUserProvider.currentUser();
        event.setOrganizationId(user.organizationId());
        event.setUserId(user.userId());
        applyScheduleRequest(event, request);
        ScheduleEvent saved = eventRepository.save(event);
        syncPosts(saved, request.posts() != null ? request.posts() : List.of());
        return toScheduleResponse(saved);
    }

    @Transactional
    public ScheduleResponse updateSchedule(Long id, ScheduleRequest request) {
        ScheduleEvent event = getOwnedEvent(id);
        applyScheduleRequest(event, request);
        ScheduleEvent saved = eventRepository.save(event);
        syncPosts(saved, request.posts() != null ? request.posts() : List.of());
        return toScheduleResponse(saved);
    }

    @Transactional
    public void deleteSchedule(Long id) {
        ScheduleEvent event = getOwnedEvent(id);
        List<Post> posts = postsFor(event);
        posts.forEach(post -> {
            if (post.getStatus() == PostStatus.POSTED) {
                post.setScheduleEventId(null);
            } else {
                postRepository.delete(post);
            }
        });
        eventRepository.delete(event);
    }

    @Transactional
    public ScheduleResponse duplicateSchedule(Long id) {
        ScheduleEvent source = getOwnedEvent(id);
        ScheduleEvent copy = new ScheduleEvent();
        copy.setOrganizationId(source.getOrganizationId());
        copy.setUserId(source.getUserId());
        copy.setName(source.getName() + " Copy");
        copy.setDescription(source.getDescription());
        copy.setColor(source.getColor());
        copy.setPlatforms(source.getPlatforms());
        copy.setMode(source.getMode());
        copy.setStartTime(source.getStartTime());
        copy.setIntervalHours(source.getIntervalHours());
        copy.setStatus("draft");
        copy.setScheduleType(source.getScheduleType());
        copy.setDaysOfWeek(source.getDaysOfWeek());
        copy.setPostingTime(source.getPostingTime());
        copy.setTimezone(source.getTimezone());
        copy.setStartDate(source.getStartDate());
        copy.setEndDate(source.getEndDate());
        copy.setDailyPostLimit(source.getDailyPostLimit());
        copy.setNotifySuccess(source.isNotifySuccess());
        copy.setNotifyFailure(source.isNotifyFailure());
        copy.setNotifyNextReminder(source.isNotifyNextReminder());
        ScheduleEvent saved = eventRepository.save(copy);
        for (Post post : postsFor(source)) {
            Post cloned = new Post();
            copyPostFields(post, cloned);
            cloned.setOrganizationId(saved.getOrganizationId());
            cloned.setUserId(saved.getUserId());
            cloned.setScheduleEventId(saved.getId());
            cloned.setStatus(PostStatus.DRAFT);
            postRepository.save(cloned);
        }
        return toScheduleResponse(saved);
    }

    @Transactional
    public ScheduleResponse togglePause(Long id) {
        ScheduleEvent event = getOwnedEvent(id);
        boolean resume = "paused".equalsIgnoreCase(event.getStatus());
        event.setStatus(resume ? "active" : "paused");
        for (Post post : postsFor(event)) {
            if (post.getStatus() == PostStatus.SCHEDULED || post.getStatus() == PostStatus.PAUSED) {
                post.setStatus(resume ? PostStatus.SCHEDULED : PostStatus.PAUSED);
                postRepository.save(post);
            }
        }
        return toScheduleResponse(eventRepository.save(event));
    }

    @Transactional
    public ScheduleResponse quickPostAction(Long scheduleId, Long postId, QuickPostActionRequest request) {
        ScheduleEvent event = getOwnedEvent(scheduleId);
        Post post = getOwnedPostInSchedule(event, postId);
        String action = request.action() != null ? request.action().trim().toLowerCase() : "";
        switch (action) {
            case "tonight" -> {
                post.setScheduledAt(todayAt(LocalTime.of(20, 0)));
                post.setStatus(PostStatus.SCHEDULED);
            }
            case "tomorrow" -> {
                post.setScheduledAt(tomorrowAt(defaultPostingTime(event)));
                post.setStatus(PostStatus.SCHEDULED);
            }
            case "best" -> {
                post.setScheduledAt(tomorrowAt(BEST_TIMES.getOrDefault(post.getPlatform(), defaultPostingTime(event))));
                post.setStatus(PostStatus.SCHEDULED);
            }
            case "retry" -> {
                post.setScheduledAt(Instant.now().plus(2, ChronoUnit.HOURS));
                post.setStatus(PostStatus.SCHEDULED);
                post.setErrorMessage(null);
                post.setRetryCount(0);
            }
            case "skip" -> post.setStatus(PostStatus.NOT_POSTED);
            default -> throw new BusinessException("Unknown quick action: " + request.action());
        }
        postRepository.save(post);
        return toScheduleResponse(event);
    }

    @Transactional
    public ScheduleResponse reschedulePost(Long scheduleId, Long postId, ReschedulePostRequest request) {
        ScheduleEvent event = getOwnedEvent(scheduleId);
        Post post = getOwnedPostInSchedule(event, postId);
        post.setScheduledAt(request.scheduledAt());
        post.setStatus(PostStatus.SCHEDULED);
        postRepository.save(post);
        return toScheduleResponse(event);
    }

    public List<ScheduleTemplateResponse> listTemplates() {
        CurrentUser user = currentUserProvider.currentUser();
        List<ScheduleTemplateResponse> custom = templateRepository
                .findByOrganizationIdAndUserIdOrderByCreatedAtDesc(user.organizationId(), user.userId())
                .stream()
                .map(this::toTemplateResponse)
                .toList();
        List<ScheduleTemplateResponse> all = new ArrayList<>(defaultTemplates());
        all.addAll(custom);
        return all;
    }

    @Transactional
    public ScheduleTemplateResponse createTemplate(ScheduleTemplateRequest request) {
        CurrentUser user = currentUserProvider.currentUser();
        ScheduleTemplate template = new ScheduleTemplate();
        template.setOrganizationId(user.organizationId());
        template.setUserId(user.userId());
        applyTemplateRequest(template, request);
        return toTemplateResponse(templateRepository.save(template));
    }

    // Legacy schedule-events API -------------------------------------------------

    public List<ScheduleEventResponse> list() {
        CurrentUser user = currentUserProvider.currentUser();
        return eventRepository
                .findByOrganizationIdAndUserIdOrderByCreatedAtDesc(user.organizationId(), user.userId())
                .stream()
                .map(this::toEventResponse)
                .toList();
    }

    @Transactional
    public ScheduleEventResponse create(CreateScheduleEventRequest request) {
        if (request.mode() == ScheduleMode.INTERVAL
                && (request.startTime() == null
                        || request.intervalHours() == null
                        || request.intervalHours() <= 0)) {
            throw new BusinessException("INTERVAL mode requires a start time and a positive interval (hours).");
        }
        CurrentUser user = currentUserProvider.currentUser();
        ScheduleEvent event = new ScheduleEvent();
        event.setOrganizationId(user.organizationId());
        event.setUserId(user.userId());
        event.setName(request.name().trim());
        event.setMode(request.mode());
        event.setStartTime(request.startTime());
        event.setIntervalHours(request.intervalHours());
        event.setStatus("active");
        event.setScheduleType(request.mode() == ScheduleMode.INTERVAL ? "custom" : "one-time");
        event.setPostingTime(LocalTime.of(20, 0));
        event.setTimezone("Asia/Dhaka");
        event.setStartDate(LocalDate.now());
        event.setPlatforms(SocialPlatform.FACEBOOK.name());
        return toEventResponse(eventRepository.save(event));
    }

    @Transactional
    public ScheduleEventResponse attachPosts(Long eventId, AttachPostsRequest request) {
        ScheduleEvent event = getOwnedEvent(eventId);
        CurrentUser user = currentUserProvider.currentUser();
        for (int i = 0; i < request.items().size(); i++) {
            ScheduledPostInput item = request.items().get(i);
            Post post = postRepository
                    .findByIdAndOrganizationIdAndUserId(item.postId(), user.organizationId(), user.userId())
                    .orElseThrow(() -> new ResourceNotFoundException("Post", item.postId()));
            if (post.getStatus() == PostStatus.POSTED) {
                throw new BusinessException("Post " + post.getId() + " is already published.");
            }
            post.setScheduledAt(computeScheduledAt(event, item, i));
            post.setScheduleEventId(event.getId());
            post.setSortOrder(i);
            post.setStatus(PostStatus.SCHEDULED);
            post.setErrorMessage(null);
            post.setRetryCount(0);
            postRepository.save(post);
        }
        return toEventResponse(event);
    }

    private void applyScheduleRequest(ScheduleEvent event, ScheduleRequest request) {
        event.setName(request.name().trim());
        event.setDescription(blankToNull(request.description()));
        event.setColor(blankToDefault(request.color(), "#4f46e5"));
        event.setPlatforms(joinEnums(request.platforms()));
        event.setStatus(normalizeStatus(request.status()));
        event.setScheduleType(request.scheduleType());
        event.setDaysOfWeek(joinStrings(request.daysOfWeek()));
        event.setPostingTime(request.postingTime());
        event.setTimezone(blankToDefault(request.timezone(), "Asia/Dhaka"));
        event.setStartDate(request.startDate());
        event.setEndDate(request.endDate());
        event.setDailyPostLimit(request.dailyPostLimit());
        ScheduleNotifications notifications = notifications(request.notifications());
        event.setNotifySuccess(notifications.publishSuccess());
        event.setNotifyFailure(notifications.failure());
        event.setNotifyNextReminder(notifications.nextPostReminder());
        event.setMode("custom".equals(request.scheduleType()) ? ScheduleMode.INTERVAL : ScheduleMode.EXPLICIT);
        event.setStartTime(request.startDate().atTime(request.postingTime()).toInstant(ZoneOffset.UTC));
        event.setIntervalHours("daily".equals(request.scheduleType()) ? 24 : null);
    }

    private void syncPosts(ScheduleEvent event, List<SchedulePostRequest> requests) {
        CurrentUser user = currentUserProvider.currentUser();
        Map<Long, Post> existing = new HashMap<>();
        for (Post post : postsFor(event)) {
            existing.put(post.getId(), post);
        }
        Set<Long> retained = new HashSet<>();
        for (int i = 0; i < requests.size(); i++) {
            SchedulePostRequest request = requests.get(i);
            Post post = request.id() != null ? existing.get(request.id()) : null;
            if (request.id() != null && post == null) {
                post = postRepository
                        .findByIdAndOrganizationIdAndUserId(request.id(), user.organizationId(), user.userId())
                        .orElseThrow(() -> new ResourceNotFoundException("Post", request.id()));
                if (post.getScheduleEventId() != null && !post.getScheduleEventId().equals(event.getId())) {
                    throw new BusinessException("Post " + request.id() + " belongs to another schedule.");
                }
            }
            if (post == null) {
                post = new Post();
                post.setOrganizationId(event.getOrganizationId());
                post.setUserId(event.getUserId());
            }
            applyPostRequest(event, post, request, i);
            Post saved = postRepository.save(post);
            retained.add(saved.getId());
        }
        for (Post post : existing.values()) {
            if (!retained.contains(post.getId())) {
                if (post.getStatus() == PostStatus.POSTED) {
                    post.setScheduleEventId(null);
                    postRepository.save(post);
                } else {
                    postRepository.delete(post);
                }
            }
        }
    }

    private void applyPostRequest(ScheduleEvent event, Post post, SchedulePostRequest request, int index) {
        post.setScheduleEventId(event.getId());
        post.setSortOrder(request.sortOrder() != null ? request.sortOrder() : index);
        post.setTitle(blankToNull(request.title()));
        post.setContent(blankToNull(request.caption()));
        post.setPlatform(request.platform());
        post.setScheduledAt(request.scheduledAt() != null
                ? request.scheduledAt()
                : event.getStartDate().atTime(event.getPostingTime()).plusDays(index).toInstant(ZoneOffset.UTC));
        post.setStatus(request.status() != null ? request.status() : PostStatus.DRAFT);
        post.setMediaUrl(blankToNull(request.mediaUrl()));
        post.setLink(blankToNull(request.link()));
        post.setHashtags(joinStrings(request.hashtags()));
        post.setCta(blankToNull(request.cta()));
        post.setTimeOverride(request.timeOverride());
        if (request.socialIntegrationId() != null) {
            integrationRepository
                    .findByIdAndOrganizationIdAndUserId(
                            request.socialIntegrationId(), event.getOrganizationId(), event.getUserId())
                    .orElseThrow(() -> new BusinessException("That page is not connected or not yours."));
        }
        post.setSocialIntegrationId(request.socialIntegrationId());
    }

    private ScheduleResponse toScheduleResponse(ScheduleEvent event) {
        List<Post> posts = postsFor(event);
        int total = posts.size();
        int posted = (int) posts.stream().filter(p -> p.getStatus() == PostStatus.POSTED).count();
        int failed = (int) posts.stream().filter(p -> p.getStatus() == PostStatus.FAILED).count();
        int pending = (int) posts.stream()
                .filter(p -> p.getStatus() == PostStatus.DRAFT
                        || p.getStatus() == PostStatus.SCHEDULED
                        || p.getStatus() == PostStatus.NOT_POSTED
                        || p.getStatus() == PostStatus.PAUSED)
                .count();
        Instant next = posts.stream()
                .filter(p -> p.getStatus() == PostStatus.SCHEDULED)
                .map(Post::getScheduledAt)
                .filter(Objects::nonNull)
                .filter(i -> !i.isBefore(Instant.now()))
                .min(Comparator.naturalOrder())
                .orElse(null);
        int completion = total == 0 ? 0 : Math.round((posted * 100f) / total);
        int health = healthScore(event, posts, posted, failed);
        return new ScheduleResponse(
                event.getId(),
                event.getName(),
                event.getDescription(),
                event.getColor(),
                parsePlatforms(event.getPlatforms()),
                normalizeStatus(event.getStatus()),
                blankToDefault(event.getScheduleType(), "one-time"),
                split(event.getDaysOfWeek()),
                defaultPostingTime(event),
                blankToDefault(event.getTimezone(), "Asia/Dhaka"),
                event.getStartDate(),
                event.getEndDate(),
                event.getDailyPostLimit(),
                new ScheduleNotifications(event.isNotifySuccess(), event.isNotifyFailure(), event.isNotifyNextReminder()),
                posts.stream().map(Post::getId).toList(),
                total,
                posted,
                pending,
                failed,
                next,
                completion,
                health,
                healthSuggestions(event, posts, failed),
                bestTimes(parsePlatforms(event.getPlatforms())),
                conflicts(event, posts),
                insights(event, posts, health),
                event.getCreatedAt(),
                event.getUpdatedAt(),
                posts.stream().map(this::toPostResponse).toList());
    }

    private SchedulePostResponse toPostResponse(Post post) {
        return new SchedulePostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getPlatform(),
                post.getScheduledAt(),
                post.getStatus(),
                post.getSocialIntegrationId(),
                post.getMediaUrl(),
                post.getLink(),
                split(post.getHashtags()),
                post.getCta(),
                post.getTimeOverride(),
                post.getPublishedAt(),
                post.getExternalPostId(),
                post.getErrorMessage(),
                post.getSortOrder(),
                new EngagementSummary(0, 0, 0, 0));
    }

    private ScheduleEventResponse toEventResponse(ScheduleEvent event) {
        List<Post> posts = postsFor(event);
        return new ScheduleEventResponse(
                event.getId(),
                event.getName(),
                event.getMode(),
                event.getStartTime(),
                event.getIntervalHours(),
                event.getStatus(),
                event.getCreatedAt(),
                posts.stream().map(postMapper::toResponse).toList());
    }

    private ScheduleTemplateResponse toTemplateResponse(ScheduleTemplate template) {
        return new ScheduleTemplateResponse(
                template.getId(),
                template.getName(),
                template.getDescription(),
                template.getColor(),
                parsePlatforms(template.getPlatforms()),
                template.getScheduleType(),
                split(template.getDaysOfWeek()),
                template.getPostingTime(),
                template.getTimezone(),
                template.getDailyPostLimit(),
                new ScheduleNotifications(template.isNotifySuccess(), template.isNotifyFailure(), template.isNotifyNextReminder()),
                template.getCreatedAt());
    }

    private void applyTemplateRequest(ScheduleTemplate template, ScheduleTemplateRequest request) {
        template.setName(request.name().trim());
        template.setDescription(blankToNull(request.description()));
        template.setColor(blankToDefault(request.color(), "#4f46e5"));
        template.setPlatforms(joinEnums(request.platforms()));
        template.setScheduleType(request.scheduleType());
        template.setDaysOfWeek(joinStrings(request.daysOfWeek()));
        template.setPostingTime(request.postingTime());
        template.setTimezone(blankToDefault(request.timezone(), "Asia/Dhaka"));
        template.setDailyPostLimit(request.dailyPostLimit());
        ScheduleNotifications notifications = notifications(request.notifications());
        template.setNotifySuccess(notifications.publishSuccess());
        template.setNotifyFailure(notifications.failure());
        template.setNotifyNextReminder(notifications.nextPostReminder());
    }

    private ScheduleEvent getOwnedEvent(Long id) {
        CurrentUser user = currentUserProvider.currentUser();
        return eventRepository
                .findByIdAndOrganizationIdAndUserId(id, user.organizationId(), user.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", id));
    }

    private Post getOwnedPostInSchedule(ScheduleEvent event, Long postId) {
        return postRepository
                .findByIdAndOrganizationIdAndUserId(postId, event.getOrganizationId(), event.getUserId())
                .filter(p -> event.getId().equals(p.getScheduleEventId()))
                .orElseThrow(() -> new ResourceNotFoundException("Post", postId));
    }

    private List<Post> postsFor(ScheduleEvent event) {
        return postRepository.findByOrganizationIdAndUserIdAndScheduleEventIdOrderBySortOrderAscScheduledAtAsc(
                event.getOrganizationId(), event.getUserId(), event.getId());
    }

    private Instant computeScheduledAt(ScheduleEvent event, ScheduledPostInput item, int index) {
        if (event.getMode() == ScheduleMode.EXPLICIT) {
            if (item.scheduledAt() == null) {
                throw new BusinessException("EXPLICIT mode requires a scheduledAt for post " + item.postId() + ".");
            }
            return item.scheduledAt();
        }
        return event.getStartTime().plus((long) index * event.getIntervalHours(), ChronoUnit.HOURS);
    }

    private int healthScore(ScheduleEvent event, List<Post> posts, int posted, int failed) {
        if (posts.isEmpty()) {
            return 58;
        }
        double success = ((double) posted / posts.size()) * 45;
        double readiness = ((double) posts.stream()
                        .filter(p -> p.getContent() != null && !p.getContent().isBlank())
                        .filter(p -> p.getMediaUrl() != null && !p.getMediaUrl().isBlank())
                        .count()
                / posts.size()) * 25;
        int overdue = (int) posts.stream()
                .filter(p -> p.getStatus() == PostStatus.SCHEDULED || p.getStatus() == PostStatus.NOT_POSTED)
                .filter(p -> p.getScheduledAt() != null && p.getScheduledAt().isBefore(Instant.now()))
                .count();
        int consistency = "active".equalsIgnoreCase(event.getStatus()) ? 20 : "paused".equalsIgnoreCase(event.getStatus()) ? 10 : 6;
        return Math.max(0, Math.min(100, (int) Math.round(success + readiness + consistency - failed * 7 - overdue * 6)));
    }

    private List<String> healthSuggestions(ScheduleEvent event, List<Post> posts, int failed) {
        List<String> suggestions = new ArrayList<>();
        if (posts.stream().anyMatch(p -> p.getScheduledAt() != null && p.getScheduledAt().isBefore(Instant.now())
                && (p.getStatus() == PostStatus.SCHEDULED || p.getStatus() == PostStatus.NOT_POSTED))) {
            suggestions.add("Reschedule overdue posts.");
        }
        if (failed > 0) {
            suggestions.add("Fix failed posts and retry publishing.");
        }
        if (posts.stream().anyMatch(p -> p.getContent() == null || p.getContent().isBlank() || p.getMediaUrl() == null || p.getMediaUrl().isBlank())) {
            suggestions.add("Add missing captions or media.");
        }
        if (event.getEndDate() == null && !"one-time".equals(event.getScheduleType())) {
            suggestions.add("Review schedules without end dates.");
        }
        return suggestions.isEmpty() ? List.of("Schedule is ready and consistent.") : suggestions;
    }

    private List<BestTimeSuggestion> bestTimes(List<SocialPlatform> platforms) {
        return platforms.stream()
                .map(platform -> {
                    LocalTime time = BEST_TIMES.getOrDefault(platform, LocalTime.of(19, 30));
                    return new BestTimeSuggestion(
                            platform,
                            toWindow(time),
                            platform == SocialPlatform.FACEBOOK || platform == SocialPlatform.INSTAGRAM
                                    ? "High engagement"
                                    : "Medium engagement",
                            time);
                })
                .toList();
    }

    private List<ConflictWarning> conflicts(ScheduleEvent event, List<Post> posts) {
        List<ConflictWarning> warnings = new ArrayList<>();
        Map<String, List<Post>> bySlot = new HashMap<>();
        for (Post post : posts) {
            if (post.getScheduledAt() == null) {
                continue;
            }
            String key = post.getPlatform() + "|" + post.getScheduledAt().truncatedTo(ChronoUnit.MINUTES);
            bySlot.computeIfAbsent(key, ignored -> new ArrayList<>()).add(post);
        }
        bySlot.values().stream().filter(group -> group.size() > 1).forEach(group -> warnings.add(new ConflictWarning(
                "slot-" + group.get(0).getId(),
                "Simultaneous platform posts",
                group.size() + " posts target " + group.get(0).getPlatform() + " at the same time.",
                "warning",
                group.stream().map(Post::getId).toList())));
        if (event.getDailyPostLimit() != null) {
            Map<LocalDate, List<Post>> byDay = new HashMap<>();
            for (Post post : posts) {
                if (post.getScheduledAt() != null) {
                    byDay.computeIfAbsent(post.getScheduledAt().atZone(ZoneOffset.UTC).toLocalDate(), ignored -> new ArrayList<>()).add(post);
                }
            }
            byDay.forEach((day, group) -> {
                if (group.size() > event.getDailyPostLimit()) {
                    warnings.add(new ConflictWarning(
                            "limit-" + day,
                            "Daily post limit exceeded",
                            group.size() + " posts are planned on " + day + "; limit is " + event.getDailyPostLimit() + ".",
                            "critical",
                            group.stream().map(Post::getId).toList()));
                }
            });
        }
        return warnings;
    }

    private List<ScheduleInsight> insights(ScheduleEvent event, List<Post> posts, int health) {
        SocialPlatform bestPlatform = posts.stream()
                .collect(java.util.stream.Collectors.groupingBy(Post::getPlatform, java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(parsePlatforms(event.getPlatforms()).stream().findFirst().orElse(SocialPlatform.FACEBOOK));
        long attention = posts.stream().filter(p -> p.getStatus() == PostStatus.FAILED || p.getStatus() == PostStatus.NOT_POSTED).count();
        Instant next = posts.stream().map(Post::getScheduledAt).filter(Objects::nonNull).filter(i -> i.isAfter(Instant.now())).min(Comparator.naturalOrder()).orElse(null);
        return List.of(
                new ScheduleInsight("best-platform", bestPlatform + " is your best-performing platform.", "good"),
                new ScheduleInsight("best-time", "8 PM posts typically get stronger engagement.", "info"),
                new ScheduleInsight("attention", attention + " posts need attention.", attention > 0 ? "warning" : "good"),
                new ScheduleInsight("next", next != null ? "Your next post is " + next + "." : "No upcoming posts are scheduled.", next != null ? "info" : "warning"),
                new ScheduleInsight("health", "Schedule health is " + health + "/100.", health >= 75 ? "good" : "warning"));
    }

    private List<ScheduleTemplateResponse> defaultTemplates() {
        return List.of(
                defaultTemplate("Daily Product Post", "One product-led post every evening.", "daily", List.of(SocialPlatform.FACEBOOK, SocialPlatform.INSTAGRAM), LocalTime.of(20, 0), "#4f46e5"),
                defaultTemplate("Weekly Offer Campaign", "Weekly promotional run with offer reminders.", "weekly", List.of(SocialPlatform.FACEBOOK, SocialPlatform.INSTAGRAM, SocialPlatform.X), LocalTime.of(19, 30), "#f97316"),
                defaultTemplate("New Arrival Launch", "Launch sequence for fresh catalog drops.", "custom", List.of(SocialPlatform.INSTAGRAM, SocialPlatform.TIKTOK, SocialPlatform.PINTEREST), LocalTime.of(18, 45), "#10b981"),
                defaultTemplate("Flash Sale", "Short high-urgency posting plan.", "one-time", List.of(SocialPlatform.FACEBOOK, SocialPlatform.INSTAGRAM, SocialPlatform.X), LocalTime.of(21, 0), "#ef4444"),
                defaultTemplate("Ramadan Campaign", "Evening campaign cadence for Ramadan offers.", "daily", List.of(SocialPlatform.FACEBOOK, SocialPlatform.INSTAGRAM, SocialPlatform.YOUTUBE), LocalTime.of(20, 15), "#7c3aed"),
                defaultTemplate("Eid Campaign", "Celebration, offer, and reminder posts.", "custom", List.of(SocialPlatform.FACEBOOK, SocialPlatform.INSTAGRAM, SocialPlatform.TIKTOK), LocalTime.of(19, 45), "#0ea5e9"),
                defaultTemplate("Customer Review Post", "Recurring social proof schedule.", "weekly", List.of(SocialPlatform.LINKEDIN, SocialPlatform.FACEBOOK), LocalTime.of(11, 30), "#14b8a6"),
                defaultTemplate("Weekend Engagement", "Polls, questions, and community posts.", "weekly", List.of(SocialPlatform.INSTAGRAM, SocialPlatform.X, SocialPlatform.FACEBOOK), LocalTime.of(20, 30), "#ec4899"),
                defaultTemplate("Monthly Content Plan", "Balanced monthly content planning starter.", "monthly", List.of(SocialPlatform.FACEBOOK, SocialPlatform.INSTAGRAM, SocialPlatform.LINKEDIN, SocialPlatform.YOUTUBE), LocalTime.of(10, 0), "#64748b"));
    }

    private ScheduleTemplateResponse defaultTemplate(
            String name, String description, String type, List<SocialPlatform> platforms, LocalTime time, String color) {
        return new ScheduleTemplateResponse(
                null,
                name,
                description,
                color,
                platforms,
                type,
                List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
                time,
                "Asia/Dhaka",
                2,
                new ScheduleNotifications(true, true, true),
                null);
    }

    private void copyPostFields(Post source, Post target) {
        target.setSocialIntegrationId(source.getSocialIntegrationId());
        target.setPlatform(source.getPlatform());
        target.setContent(source.getContent());
        target.setTitle(source.getTitle());
        target.setLink(source.getLink());
        target.setMediaUrl(source.getMediaUrl());
        target.setProductId(source.getProductId());
        target.setScheduledAt(source.getScheduledAt());
        target.setHashtags(source.getHashtags());
        target.setCta(source.getCta());
        target.setTimeOverride(source.getTimeOverride());
        target.setSortOrder(source.getSortOrder());
    }

    private ScheduleNotifications notifications(ScheduleNotifications value) {
        return value != null ? value : new ScheduleNotifications(true, true, true);
    }

    private String normalizeStatus(String status) {
        return status == null || status.isBlank() ? "draft" : status.trim().toLowerCase();
    }

    private LocalTime defaultPostingTime(ScheduleEvent event) {
        return event.getPostingTime() != null ? event.getPostingTime() : LocalTime.of(20, 0);
    }

    private Instant todayAt(LocalTime time) {
        return LocalDate.now().atTime(time).toInstant(ZoneOffset.UTC);
    }

    private Instant tomorrowAt(LocalTime time) {
        return LocalDate.now().plusDays(1).atTime(time).toInstant(ZoneOffset.UTC);
    }

    private String toWindow(LocalTime time) {
        return time + "-" + time.plusHours(1);
    }

    private String joinEnums(List<SocialPlatform> values) {
        return values == null ? "" : values.stream().map(Enum::name).collect(java.util.stream.Collectors.joining(","));
    }

    private String joinStrings(List<String> values) {
        return values == null ? "" : values.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).collect(java.util.stream.Collectors.joining(","));
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    private List<SocialPlatform> parsePlatforms(String value) {
        if (value == null || value.isBlank()) {
            return List.of(SocialPlatform.FACEBOOK);
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(SocialPlatform::valueOf)
                .toList();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}

