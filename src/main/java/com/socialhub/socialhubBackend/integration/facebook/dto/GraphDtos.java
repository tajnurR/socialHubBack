package com.socialhub.socialhubBackend.integration.facebook.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Raw Meta Graph API response shapes (only the fields we consume). */
public final class GraphDtos {

    private GraphDtos() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page(String id, String name, @JsonProperty("access_token") String accessToken) {}

    /** Page profile info for the dashboard header (best-effort; fields may be absent). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PageProfile(
            String id,
            String name,
            String category,
            @JsonProperty("fan_count") Long fanCount,
            Picture picture) {
        public String pictureUrl() {
            return picture != null && picture.data() != null ? picture.data().url() : null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Picture(PictureData data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PictureData(String url) {}

    /** Response of GET /me/accounts — pages the (user) token manages, each with a Page token. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AccountsResponse(List<Page> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PostsResponse(List<Post> data, Paging paging) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Post(
            String id,
            String message,
            @JsonProperty("created_time") String createdTime,
            @JsonProperty("full_picture") String fullPicture,
            @JsonProperty("permalink_url") String permalinkUrl,
            Shares shares,
            Engagement likes,
            Engagement comments,
            Engagement reactions) {

        public long likeCount() {
            return likes != null ? likes.totalCount() : 0L;
        }

        public long commentCount() {
            return comments != null ? comments.totalCount() : 0L;
        }

        public long shareCount() {
            return shares != null && shares.count() != null ? shares.count() : 0L;
        }

        public long reactionCount() {
            return reactions != null ? reactions.totalCount() : 0L;
        }
    }

    /** {@code shares} object on a post ({@code {"count": N}}); absent when zero. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Shares(Long count) {}

    /** {@code likes}/{@code comments} edge requested with {@code .summary(true)}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Engagement(Summary summary) {
        public long totalCount() {
            return summary != null && summary.totalCount() != null ? summary.totalCount() : 0L;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(@JsonProperty("total_count") Long totalCount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Paging(Cursors cursors, String next) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cursors(String before, String after) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateResponse(String id) {}

    /** Response of GET /oauth/access_token (token exchange). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") Long expiresIn) {}
}
