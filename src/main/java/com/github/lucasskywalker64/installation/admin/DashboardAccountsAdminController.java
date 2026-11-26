package com.github.lucasskywalker64.installation.admin;

import com.github.lucasskywalker64.dashboard.DashboardRecovery;
import com.github.lucasskywalker64.dashboard.DiscordDashboardOAuth;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/api/accounts")
class DashboardAccountsAdminController {
    private static final int MAX_REASON_LENGTH = 300;
    private static final int MAX_QUERY_LENGTH = 64;

    private final DashboardRecovery recovery;
    private final LocalAdministrator administrator;

    DashboardAccountsAdminController(DashboardRecovery recovery, LocalAdministrator administrator) {
        this.recovery = recovery;
        this.administrator = administrator;
    }

    @GetMapping
    ResponseEntity<DashboardRecovery.AccountPage> accounts(@RequestParam(defaultValue = "") String query,
                                                           @RequestParam(defaultValue = "0") int page) {
        if (page < 0 || query.length() > MAX_QUERY_LENGTH) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(recovery.listAccounts(query.strip(), page));
    }

    @PostMapping("/{id}/recovery-link")
    ResponseEntity<DashboardRecovery.IssuedLink> issue(@PathVariable UUID id, @RequestBody LinkRequest request,
            @AuthenticationPrincipal LocalAdministrator.AdminPrincipal principal) {
        String reason = request.reason() == null ? "" : request.reason().strip();
        if (reason.isEmpty() || reason.length() > MAX_REASON_LENGTH || request.loginVersion() == null) {
            return ResponseEntity.badRequest().build();
        }
        administrator.verifyCurrentPassword(principal, request.password());
        return ResponseEntity.ok(recovery.issueLink(id, request.loginVersion(), principal.username(), reason));
    }

    @PostMapping("/{id}/revoke-recovery-link")
    ResponseEntity<Void> revoke(@PathVariable UUID id,
                                @AuthenticationPrincipal LocalAdministrator.AdminPrincipal principal) {
        recovery.revokeLink(id, principal.username());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(DashboardRecovery.AccountMissing.class)
    ResponseEntity<Void> accountMissing() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(DashboardRecovery.StaleAccount.class)
    ResponseEntity<Void> staleAccount() {
        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).build();
    }

    @ExceptionHandler(DiscordDashboardOAuth.UnavailableSettings.class)
    ResponseEntity<Void> unavailable() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<Void> badCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @ExceptionHandler(LockedException.class)
    ResponseEntity<Void> rateLimited() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
    }

    record LinkRequest(@Nullable String password, @Nullable String reason, @Nullable Long loginVersion) {
        @Override
        public String toString() {
            return "LinkRequest[redacted]";
        }
    }
}
