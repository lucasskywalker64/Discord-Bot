package com.github.lucasskywalker64.installation.admin;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
class LocalAdministrator {
    static final String AUTHORITY = "LOCAL_ADMIN";
    static final String PASSWORD_LENGTH_MESSAGE = "Use a passphrase of 12 to 256 characters.";
    private static final String PASSWORD_ALGORITHM = "pbkdf2@SpringSecurity_v5_8";
    private static final Pattern USERNAME = Pattern.compile("[a-zA-Z0-9._-]{3,64}");
    private static final int MAX_ATTEMPTS = 8;
    private static final long ATTEMPT_WINDOW_NANOS = Duration.ofMinutes(1).toNanos();

    private final JdbcClient jdbc;
    private final PasswordEncoder passwords = new DelegatingPasswordEncoder(PASSWORD_ALGORITHM,
            Map.of(PASSWORD_ALGORITHM, Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    private int attempts;
    private long windowDeadline;

    LocalAdministrator(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    boolean claimAvailable() {
        return !jdbc.sql("SELECT EXISTS (SELECT 1 FROM local_administrator)").query(Boolean.class).single();
    }

    boolean claim(String username, String password) {
        return jdbc.sql("""
                INSERT INTO local_administrator (id, username, password_hash)
                VALUES (1, :username, :hash) ON CONFLICT (id) DO NOTHING
                """)
                .param("username", username).param("hash", passwords.encode(password))
                .update() == 1;
    }

    Authentication authenticate(Authentication attempt) {
        takeAttempt();
        var supplied = (String) attempt.getCredentials();
        var credential = readCredential(attempt.getName());
        verify(supplied, credential);
        var principal = new AdminPrincipal(attempt.getName(), credential.orElseThrow().version());
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority(AUTHORITY)));
    }

    boolean isCurrent(AdminPrincipal principal) {
        return jdbc.sql("SELECT credential_version FROM local_administrator WHERE id = 1 AND username = :username")
                .param("username", principal.username()).query(Long.class).optional()
                .filter(version -> version == principal.version()).isPresent();
    }

    boolean changePassword(AdminPrincipal principal, String currentPassword, String newPassword) {
        takeAttempt();
        verify(currentPassword, readCredential(principal.username())
                .filter(credential -> credential.version() == principal.version()));
        return jdbc.sql("""
                UPDATE local_administrator SET password_hash = :hash, credential_version = credential_version + 1
                WHERE id = 1 AND credential_version = :version
                """).param("hash", passwords.encode(newPassword)).param("version", principal.version()).update() == 1;
    }

    boolean resetPassword(String password) {
        return jdbc.sql("""
                UPDATE local_administrator SET password_hash = :hash, credential_version = credential_version + 1
                WHERE id = 1
                """).param("hash", passwords.encode(password)).update() == 1;
    }

    static boolean validUsername(String username) {
        return USERNAME.matcher(username).matches();
    }

    static boolean validPassword(String password) {
        return password.length() >= 12 && password.length() <= 256 && !password.isBlank();
    }

    private Optional<Credential> readCredential(String username) {
        return jdbc.sql("""
                SELECT password_hash, credential_version FROM local_administrator WHERE id = 1 AND username = :username
                """).param("username", username)
                .query((row, _) -> new Credential(row.getString(1), row.getLong(2))).optional();
    }

    private void verify(String password, Optional<Credential> credential) {
        if (password == null || !validPassword(password)
                || credential.isEmpty() || !passwords.matches(password, credential.orElseThrow().hash())) {
            throw new BadCredentialsException("Invalid local administrator credentials.");
        }
    }

    private synchronized void takeAttempt() {
        long now = System.nanoTime();
        if (attempts == 0 || now - windowDeadline >= 0) {
            attempts = 0;
            windowDeadline = now + ATTEMPT_WINDOW_NANOS;
        }
        if (attempts >= MAX_ATTEMPTS) {
            throw new LockedException("Too many local administrator login attempts.");
        }
        attempts++;
    }

    record AdminPrincipal(String username, long version) implements Principal {
        @Override
        public String getName() {
            return username;
        }
    }

    private record Credential(String hash, long version) {
        @Override
        public String toString() {
            return "Credential[version=" + version + "]";
        }
    }
}
