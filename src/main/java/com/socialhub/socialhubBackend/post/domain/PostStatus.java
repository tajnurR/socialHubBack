package com.socialhub.socialhubBackend.post.domain;

/** Lifecycle of a post: imported/created → scheduled → published (or failed). */
public enum PostStatus {
    DRAFT,
    SCHEDULED,
    POSTED,
    NOT_POSTED,
    FAILED,
    PAUSED
}
