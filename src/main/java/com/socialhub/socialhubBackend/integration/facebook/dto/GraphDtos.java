package com.socialhub.socialhubBackend.integration.facebook.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Raw Meta Graph API response shapes (only the fields we consume). */
public final class GraphDtos {

    private GraphDtos() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page(String id, String name, @JsonProperty("access_token") String accessToken) {}

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
            @JsonProperty("permalink_url") String permalinkUrl) {}

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
