package com.github.lucasskywalker64.dashboard;

import com.github.lucasskywalker64.dashboard.DashboardAccounts.Principal;
import com.github.lucasskywalker64.dashboard.DashboardController.ReplacementView;
import com.github.lucasskywalker64.dashboard.DashboardWeb.Outcome;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.github.lucasskywalker64.dashboard.DashboardWeb.dashboard;
import static com.github.lucasskywalker64.dashboard.DashboardWeb.redirect;

@RestController
@RequestMapping("/dashboard/api")
class DashboardRecoveryController {
    private final DiscordDashboardOAuth oauth;
    private final DashboardRecovery recovery;

    DashboardRecoveryController(DiscordDashboardOAuth oauth, DashboardRecovery recovery) {
        this.oauth = oauth;
        this.recovery = recovery;
    }

    @PostMapping("/recover")
    ResponseEntity<Void> start(@RequestParam String credential, @AuthenticationPrincipal @Nullable Principal principal,
                               HttpServletRequest request, HttpServletResponse response) {
        if (principal != null) {
            return dashboard(Outcome.RECOVERY_SIGNED_IN);
        }
        var settings = oauth.settings();
        if (settings == null) {
            return dashboard(Outcome.UNAVAILABLE);
        }
        var claim = recovery.begin(credential);
        if (claim.isEmpty()) {
            return dashboard(Outcome.RECOVERY_INVALID);
        }
        var browser = DashboardWeb.oauthBrowser(request, response, settings.callbackUrl());
        return redirect(oauth.beginRecovery(settings, browser, claim.orElseThrow()));
    }

    @GetMapping("/recovery")
    ResponseEntity<ReplacementView> proof(HttpServletRequest request,
                                          @AuthenticationPrincipal @Nullable Principal principal) {
        var proof = oauth.proof(browser(request));
        if (principal != null || proof == null || proof.recovery() == null
                || oauth.settings(proof.revision()) == null || !recovery.current(proof.recovery())) {
            return ResponseEntity.status(HttpStatus.GONE).build();
        }
        return ResponseEntity.ok(new ReplacementView(proof.confirmation(), proof.current(), proof.proposed()));
    }

    @PostMapping("/confirm-recovery")
    ResponseEntity<Void> confirm(@RequestParam String confirmation, HttpServletRequest request,
                                 @AuthenticationPrincipal @Nullable Principal principal) {
        if (principal != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        var proof = oauth.confirm(browser(request), confirmation);
        if (proof == null || proof.recovery() == null || oauth.settings(proof.revision()) == null) {
            return ResponseEntity.status(HttpStatus.GONE).build();
        }
        recovery.complete(proof.recovery(), proof.proposed());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/discard-recovery")
    ResponseEntity<Void> discard(@RequestParam String confirmation, HttpServletRequest request) {
        oauth.confirm(browser(request), confirmation);
        return dashboard();
    }

    @ExceptionHandler(DashboardRecovery.InvalidCredential.class)
    ResponseEntity<Void> invalidCredential() {
        return ResponseEntity.status(HttpStatus.GONE).build();
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Void> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(DiscordDashboardOAuth.AttemptsFull.class)
    ResponseEntity<Void> attemptsFull() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    private static @Nullable String browser(HttpServletRequest request) {
        return DashboardWeb.cookie(request, DashboardWeb.OAUTH_COOKIE);
    }

}
