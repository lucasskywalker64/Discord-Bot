package com.github.lucasskywalker64.persistence.data;

import com.github.lucasskywalker64.api.twitch.auth.TwitchOAuthService.TokenBundle;

public record TokenData(TokenBundle bundle, String userId, String login) {
    public TokenData withTokenBundle(TokenBundle bundle) {
        return new TokenData(bundle, userId, login);
    }
}
