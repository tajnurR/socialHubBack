package com.socialhub.socialhubBackend.integration.facebook;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.common.security.EncryptionService;
import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import com.socialhub.socialhubBackend.integration.core.domain.IntegrationStatus;
import com.socialhub.socialhubBackend.integration.core.domain.SocialIntegration;
import com.socialhub.socialhubBackend.integration.core.dto.IntegrationResponse;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderPost;
import com.socialhub.socialhubBackend.integration.core.exception.ProviderAuthException;
import com.socialhub.socialhubBackend.integration.core.service.IntegrationService;
import com.socialhub.socialhubBackend.integration.core.service.IntegrationStatusUpdater;
import com.socialhub.socialhubBackend.integration.facebook.credential.FacebookAppCredentialProvider;
import com.socialhub.socialhubBackend.integration.facebook.dto.FacebookAnalyticsDtos.AnalyticsDashboard;
import com.socialhub.socialhubBackend.integration.facebook.dto.FacebookAnalyticsDtos.BestPost;
import com.socialhub.socialhubBackend.integration.facebook.dto.FacebookAnalyticsDtos.ConnectedPage;
import com.socialhub.socialhubBackend.integration.facebook.dto.FacebookAnalyticsDtos.KpiSummary;
import com.socialhub.socialhubBackend.integration.facebook.dto.FacebookAnalyticsDtos.PageInfo;
import com.socialhub.socialhubBackend.integration.facebook.dto.FacebookAnalyticsDtos.PeriodComparison;
import com.socialhub.socialhubBackend.integration.facebook.dto.FacebookAnalyticsDtos.PostRow;
import com.socialhub.socialhubBackend.integration.facebook.dto.FacebookAnalyticsDtos.TimeSeriesPoint;
import com.socialhub.socialhubBackend.integration.facebook.dto.GraphDtos;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Builds the Facebook Page analytics dashboard: page info + KPI summary +
 * previous-period comparison + time-series (bucketed) + filtered/sorted posts.
 *
 * <p>Engagement = reactions + comments + shares. Filtering/sorting/bucketing are
 * done server-side. The expensive Graph fetch (posts within a date range) is
 * cached briefly per (integration, range) to respect Graph rate limits; filters
 * are applied in-memory so the UI stays responsive. Page profile is best-effort
 * and degrades gracefully (post-level metrics are always shown).
 */
@Service
public class FacebookAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(FacebookAnalyticsService.class);

    private static final int PER_PAGE = 25;
    private static final int MAX_POSTS = 200;
    private static final long CACHE_TTL_MS = 60_000;

    public enum SortBy { DATE, LIKES, COMMENTS, SHARES, ENGAGEMENT }

    public enum SortOrder { ASC, DESC }

    public enum Granularity { DAY, WEEK }

    private record FetchResult(List<ProviderPost> posts, boolean capped) {}

    private record CacheEntry(Instant expiresAt, FetchResult result) {}

    private record Totals(long posts, long likes, long comments, long shares, long reactions, long engagement) {}

    private final IntegrationService integrationService;
    private final FacebookProvider facebookProvider;
    private final FacebookGraphClient graphClient;
    private final EncryptionService encryptionService;
    private final IntegrationStatusUpdater statusUpdater;
    private final FacebookAppCredentialProvider appCredentialProvider;

    private final Map<String, CacheEntry> postsCache = new ConcurrentHashMap<>();

    public FacebookAnalyticsService(
            IntegrationService integrationService,
            FacebookProvider facebookProvider,
            FacebookGraphClient graphClient,
            EncryptionService encryptionService,
            IntegrationStatusUpdater statusUpdater,
            FacebookAppCredentialProvider appCredentialProvider) {
        this.integrationService = integrationService;
        this.facebookProvider = facebookProvider;
        this.graphClient = graphClient;
        this.encryptionService = encryptionService;
        this.statusUpdater = statusUpdater;
        this.appCredentialProvider = appCredentialProvider;
    }

    /** Connected Facebook Pages for the current organization. */
    public List<ConnectedPage> connectedPages() {
        return integrationService.listConnected().stream()
                .filter(i -> i.platform() == SocialPlatform.FACEBOOK)
                .map(this::toConnectedPage)
                .toList();
    }

    public AnalyticsDashboard analytics(
            Long integrationId,
            String from,
            String to,
            Long minLikes,
            Long minComments,
            SortBy sortBy,
            SortOrder order,
            Granularity granularity) {
        SocialIntegration integration = integrationService.getOwnedIntegration(integrationId);
        if (integration.getPlatform() != SocialPlatform.FACEBOOK) {
            throw new BusinessException("This integration is not a Facebook Page.");
        }
        String token = encryptionService.decrypt(integration.getAccessToken());
        String pageId = integration.getExternalAccountId();
        // Per-config Graph version override (null → global default).
        String apiVersion = appCredentialProvider.apiVersionForConfig(integration.getAppCredentialId());

        FetchResult fetched = fetchPostsCached(integrationId, pageId, token, from, to, apiVersion);
        List<ProviderPost> filtered = applyFilters(fetched.posts(), minLikes, minComments);

        List<PostRow> rows = filtered.stream()
                .sorted(comparator(sortBy, order))
                .map(this::toPostRow)
                .toList();

        PageInfo pageInfo = loadPageInfo(integration, token, apiVersion);
        KpiSummary summary = summarize(filtered);
        List<TimeSeriesPoint> series = buildSeries(filtered, granularity);
        PeriodComparison comparison = buildComparison(
                integrationId, pageId, token, from, to, minLikes, minComments, summary, apiVersion);

        return new AnalyticsDashboard(pageInfo, summary, comparison, series, rows, fetched.capped());
    }

    // --- fetching (cached) ---------------------------------------------------

    private FetchResult fetchPostsCached(
            Long integrationId, String pageId, String token, String from, String to, String apiVersion) {
        String key = integrationId + "|" + nullToEmpty(from) + "|" + nullToEmpty(to);
        CacheEntry entry = postsCache.get(key);
        if (entry != null && Instant.now().isBefore(entry.expiresAt())) {
            return entry.result();
        }
        FetchResult result = fetchPosts(integrationId, pageId, token, from, to, apiVersion);
        postsCache.put(key, new CacheEntry(Instant.now().plusMillis(CACHE_TTL_MS), result));
        return result;
    }

    private FetchResult fetchPosts(
            Long integrationId, String pageId, String token, String from, String to, String apiVersion) {
        List<ProviderPost> posts = new ArrayList<>();
        boolean capped = false;
        String cursor = null;
        try {
            while (posts.size() < MAX_POSTS) {
                GraphDtos.PostsResponse response =
                        graphClient.getPublishedPosts(pageId, token, cursor, PER_PAGE, from, to, apiVersion);
                List<GraphDtos.Post> data = response.data() != null ? response.data() : List.of();
                data.forEach(p -> posts.add(facebookProvider.toProviderPost(p)));
                cursor = response.paging() != null && response.paging().cursors() != null
                        ? response.paging().cursors().after()
                        : null;
                if (data.isEmpty() || cursor == null) {
                    break;
                }
                if (posts.size() >= MAX_POSTS) {
                    capped = true;
                    break;
                }
            }
        } catch (ProviderAuthException ex) {
            statusUpdater.markReauthRequired(integrationId);
            throw ex;
        }
        return new FetchResult(posts, capped);
    }

    // --- aggregation ---------------------------------------------------------

    private List<ProviderPost> applyFilters(List<ProviderPost> posts, Long minLikes, Long minComments) {
        return posts.stream()
                .filter(p -> minLikes == null || p.likeCount() >= minLikes)
                .filter(p -> minComments == null || p.commentCount() >= minComments)
                .toList();
    }

    private long engagement(ProviderPost p) {
        return p.reactionCount() + p.commentCount() + p.shareCount();
    }

    private Totals totals(List<ProviderPost> posts) {
        long likes = posts.stream().mapToLong(ProviderPost::likeCount).sum();
        long comments = posts.stream().mapToLong(ProviderPost::commentCount).sum();
        long shares = posts.stream().mapToLong(ProviderPost::shareCount).sum();
        long reactions = posts.stream().mapToLong(ProviderPost::reactionCount).sum();
        long engagement = reactions + comments + shares;
        return new Totals(posts.size(), likes, comments, shares, reactions, engagement);
    }

    private KpiSummary summarize(List<ProviderPost> posts) {
        Totals t = totals(posts);
        double avg = t.posts() == 0
                ? 0
                : Math.round(((double) t.engagement() / t.posts()) * 100.0) / 100.0;
        BestPost best = posts.stream()
                .max(Comparator.comparingLong(this::engagement))
                .map(p -> new BestPost(p.externalId(), p.message(), engagement(p), p.permalinkUrl()))
                .orElse(null);
        return new KpiSummary(
                t.posts(), t.likes(), t.comments(), t.shares(), t.reactions(), t.engagement(), avg, best);
    }

    private List<TimeSeriesPoint> buildSeries(List<ProviderPost> posts, Granularity granularity) {
        Granularity g = granularity != null ? granularity : Granularity.DAY;
        // TreeMap keyed by ISO date string → chronological order.
        Map<String, long[]> buckets = new TreeMap<>();
        for (ProviderPost p : posts) {
            if (p.createdTime() == null) {
                continue;
            }
            // index: 0=posts 1=likes 2=comments 3=shares 4=reactions
            long[] acc = buckets.computeIfAbsent(bucketKey(p.createdTime(), g), k -> new long[5]);
            acc[0] += 1;
            acc[1] += p.likeCount();
            acc[2] += p.commentCount();
            acc[3] += p.shareCount();
            acc[4] += p.reactionCount();
        }
        List<TimeSeriesPoint> series = new ArrayList<>();
        buckets.forEach((date, a) ->
                series.add(new TimeSeriesPoint(date, a[0], a[1], a[2], a[3], a[4], a[4] + a[2] + a[3])));
        return series;
    }

    private String bucketKey(Instant instant, Granularity granularity) {
        LocalDate date = instant.atZone(ZoneOffset.UTC).toLocalDate();
        if (granularity == Granularity.WEEK) {
            date = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
        return date.toString();
    }

    private PeriodComparison buildComparison(
            Long integrationId,
            String pageId,
            String token,
            String from,
            String to,
            Long minLikes,
            Long minComments,
            KpiSummary current,
            String apiVersion) {
        LocalDate fromDate = parseDate(from);
        LocalDate toDate = parseDate(to);
        if (fromDate == null || toDate == null || !toDate.isAfter(fromDate)) {
            return null; // comparison only when an explicit, valid range is given
        }
        long lengthDays = ChronoUnit.DAYS.between(fromDate, toDate);
        LocalDate prevTo = fromDate.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(lengthDays);

        FetchResult prev = fetchPostsCached(
                integrationId, pageId, token, prevFrom.toString(), prevTo.toString(), apiVersion);
        Totals p = totals(applyFilters(prev.posts(), minLikes, minComments));

        return new PeriodComparison(
                pct(current.totalPosts(), p.posts()),
                pct(current.totalLikes(), p.likes()),
                pct(current.totalComments(), p.comments()),
                pct(current.totalShares(), p.shares()),
                pct(current.totalReactions(), p.reactions()),
                pct(current.totalEngagement(), p.engagement()));
    }

    /** Percent change vs previous; null when previous is 0 (undefined). */
    private Double pct(long current, long previous) {
        if (previous == 0) {
            return null;
        }
        return Math.round((((double) current - previous) / previous) * 1000.0) / 10.0;
    }

    private Comparator<ProviderPost> comparator(SortBy sortBy, SortOrder order) {
        SortBy by = sortBy != null ? sortBy : SortBy.DATE;
        Comparator<ProviderPost> comparator = switch (by) {
            case LIKES -> Comparator.comparingLong(ProviderPost::likeCount);
            case COMMENTS -> Comparator.comparingLong(ProviderPost::commentCount);
            case SHARES -> Comparator.comparingLong(ProviderPost::shareCount);
            case ENGAGEMENT -> Comparator.comparingLong(this::engagement);
            case DATE -> Comparator.comparing(
                    ProviderPost::createdTime, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return order == SortOrder.ASC ? comparator : comparator.reversed();
    }

    // --- page info (best-effort) ---------------------------------------------

    private PageInfo loadPageInfo(SocialIntegration integration, String token, String apiVersion) {
        boolean tokenHealthy = integration.getStatus() == IntegrationStatus.CONNECTED;
        try {
            GraphDtos.PageProfile profile =
                    graphClient.getPageInfo(integration.getExternalAccountId(), token, apiVersion);
            return new PageInfo(
                    integration.getExternalAccountId(),
                    profile.name() != null ? profile.name() : integration.getDisplayName(),
                    profile.category(),
                    profile.pictureUrl(),
                    profile.fanCount(),
                    tokenHealthy);
        } catch (RuntimeException ex) {
            // Degrade gracefully: still render the dashboard with what we know.
            log.debug("Page info unavailable for page {}: {}",
                    integration.getExternalAccountId(), ex.getMessage());
            return new PageInfo(
                    integration.getExternalAccountId(),
                    integration.getDisplayName(),
                    null,
                    null,
                    null,
                    tokenHealthy);
        }
    }

    // --- mapping / utils ------------------------------------------------------

    private PostRow toPostRow(ProviderPost p) {
        return new PostRow(
                p.externalId(),
                p.message(),
                p.fullPicture(),
                p.permalinkUrl(),
                p.createdTime(),
                p.likeCount(),
                p.commentCount(),
                p.shareCount(),
                p.reactionCount(),
                engagement(p));
    }

    private ConnectedPage toConnectedPage(IntegrationResponse i) {
        return new ConnectedPage(i.id(), i.externalAccountId(), i.displayName(), i.status());
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
