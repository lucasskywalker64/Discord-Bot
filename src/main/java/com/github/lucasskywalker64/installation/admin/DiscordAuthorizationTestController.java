package com.github.lucasskywalker64.installation.admin;

import com.github.lucasskywalker64.dashboard.DiscordDashboardOAuth;
import com.github.lucasskywalker64.dashboard.DiscordDashboardOAuth.AdminTest;
import com.github.lucasskywalker64.dashboard.DiscordDashboardOAuth.TestStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/admin/api/discord-authorization-test")
class DiscordAuthorizationTestController {
    private static final String TEST = DiscordAuthorizationTestController.class.getName();

    private final DiscordDashboardOAuth oauth;
    private final LocalAdministrator administrator;

    DiscordAuthorizationTestController(DiscordDashboardOAuth oauth, LocalAdministrator administrator) {
        this.oauth = oauth;
        this.administrator = administrator;
    }

    @PostMapping
    TestView start(HttpSession session, @AuthenticationPrincipal LocalAdministrator.AdminPrincipal principal) {
        String sessionId = session.getId();
        var test = oauth.startTest(candidate -> {
            try {
                return sessionId.equals(session.getId()) && session.getAttribute(TEST) == candidate
                        && administrator.isCurrent(principal);
            } catch (IllegalStateException _) {
                return false;
            }
        });
        session.setAttribute(TEST, test);
        return new TestView(TestStatus.PENDING, test.launchUrl());
    }

    @GetMapping
    TestView status(HttpServletRequest request) {
        var session = request.getSession(false);
        var test = session == null ? null : (AdminTest) session.getAttribute(TEST);
        return new TestView(oauth.testStatus(test), null);
    }

    @ExceptionHandler(DiscordDashboardOAuth.UnavailableSettings.class)
    ResponseEntity<Void> unavailableSettings() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(DiscordDashboardOAuth.AttemptsFull.class)
    ResponseEntity<Void> attemptsFull() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    record TestView(TestStatus status, URI launchUrl) {
    }
}
