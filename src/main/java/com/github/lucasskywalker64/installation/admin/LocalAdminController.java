package com.github.lucasskywalker64.installation.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/api")
class LocalAdminController {
    private final LocalAdministrator administrator;
    LocalAdminController(LocalAdministrator administrator) {
        this.administrator = administrator;
    }

    @GetMapping("/session")
    SessionView session(@AuthenticationPrincipal LocalAdministrator.AdminPrincipal principal, CsrfToken csrf) {
        return new SessionView(administrator.claimAvailable(), principal != null,
                principal == null ? null : principal.username(), csrf.getToken());
    }

    @PostMapping(path = "/claim", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<?> claim(@RequestParam String username, @RequestParam String password) {
        if (!administrator.claimAvailable()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        if (!LocalAdministrator.validUsername(username)) {
            return invalid("username", "Use 3 to 64 letters, digits, periods, underscores or hyphens.");
        }
        if (!LocalAdministrator.validPassword(password)) {
            return invalid("password", LocalAdministrator.PASSWORD_LENGTH_MESSAGE);
        }
        return administrator.claim(username, password) ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @PostMapping(path = "/password", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<?> changePassword(@AuthenticationPrincipal LocalAdministrator.AdminPrincipal principal,
                                     @RequestParam String currentPassword, @RequestParam String newPassword,
                                     Authentication authentication, HttpServletRequest request,
                                     HttpServletResponse response) {
        if (!LocalAdministrator.validPassword(newPassword)) {
            return invalid("newPassword", LocalAdministrator.PASSWORD_LENGTH_MESSAGE);
        }
        try {
            if (!administrator.changePassword(principal, currentPassword, newPassword)) {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }
        } catch (BadCredentialsException _) {
            return invalid("currentPassword", "The current password is incorrect.");
        } catch (LockedException _) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<Map<String, Map<String, String>>> invalid(String field, String message) {
        return ResponseEntity.badRequest().body(Map.of("errors", Map.of(field, message)));
    }

    record SessionView(boolean claimAvailable, boolean authenticated, String username, String csrfToken) {
    }
}
