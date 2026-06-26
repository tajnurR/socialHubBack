package com.socialhub.socialhubBackend.user.context;

/**
 * The single source of truth for "who is making this request".
 *
 * <p>Every per-user/per-org config lookup goes through this. Login/SSO does not
 * exist yet, so the active implementation returns a seeded dev user
 * ({@link DevCurrentUserProvider}). <b>This is the only place that changes when
 * real authentication is added</b> — swap in an implementation that reads the
 * authenticated principal (see TODO[SSO]).
 */
public interface CurrentUserProvider {

    CurrentUser currentUser();
}
