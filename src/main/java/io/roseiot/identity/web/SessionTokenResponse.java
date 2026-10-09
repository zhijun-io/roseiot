package io.roseiot.identity.web;

import io.roseiot.identity.application.UserSessionService;

/** Access and refresh token pair returned by login, refresh and noauth activation flows. */
public record SessionTokenResponse(String accessToken, String refreshToken) {

    public static SessionTokenResponse from(UserSessionService.SessionTokens tokens) {
        return new SessionTokenResponse(tokens.accessToken(), tokens.refreshToken());
    }
}
