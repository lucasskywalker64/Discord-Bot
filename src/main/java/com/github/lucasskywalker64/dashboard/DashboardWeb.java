package com.github.lucasskywalker64.dashboard;

import com.github.lucasskywalker64.installation.DiscordCallbackUrl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.util.WebUtils;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class DashboardWeb implements WebMvcConfigurer {
    static final String SESSION_COOKIE = "DISCORD_BOT_DASHBOARD";
    static final String OAUTH_COOKIE = "DISCORD_BOT_DISCORD_OAUTH";
    static final String TEST_COOKIE = "DISCORD_BOT_DISCORD_TEST";

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/dashboard", "/dashboard/");
        registry.addViewController("/dashboard/").setViewName("forward:/dashboard/index.html");
    }

    @Bean
    CookieCsrfTokenRepository dashboardCsrfTokens() {
        var csrf = new CookieCsrfTokenRepository();
        csrf.setCookieName("DISCORD_BOT_DASHBOARD_CSRF");
        csrf.setHeaderName("X-CSRF-TOKEN");
        csrf.setCookieCustomizer(cookie -> cookie.path("/dashboard").sameSite("Strict"));
        return csrf;
    }

    @Bean
    @Order(2)
    SecurityFilterChain dashboard(HttpSecurity http, DashboardAccounts accounts, CookieCsrfTokenRepository csrf) {
        return http.securityMatcher("/dashboard", "/dashboard/**")
                // Restoring a dashboard session from its cookie is ordinary request authentication.
                .sessionManagement(AbstractHttpConfigurer::disable)
                .securityContext(context -> context.securityContextRepository(new RequestAttributeSecurityContextRepository()))
                .requestCache(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .csrf(config -> config.csrfTokenRepository(csrf))
                .addFilterBefore((request, response, chain) -> {
                    var token = cookie((HttpServletRequest) request, SESSION_COOKIE);
                    accounts.authenticate(token).ifPresent(principal ->
                            SecurityContextHolder.getContext().setAuthentication(
                                    UsernamePasswordAuthenticationToken.authenticated(principal, null,
                                            List.of(new SimpleGrantedAuthority(DashboardAccounts.AUTHORITY)))));
                    chain.doFilter(request, response);
                }, AnonymousAuthenticationFilter.class)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .headers(headers -> headers
                        .referrerPolicy(policy -> policy.policy(ReferrerPolicy.NO_REFERRER))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; frame-ancestors 'none'; base-uri 'none'; "
                                        + "form-action 'self' https://discord.com")))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/dashboard", "/dashboard/", "/dashboard/index.html",
                                "/dashboard/assets/**", "/dashboard/api/session",
                                "/dashboard/api/recovery", DiscordCallbackUrl.PATH).permitAll()
                        .requestMatchers(HttpMethod.POST, "/dashboard/api/sign-in", "/dashboard/api/recover",
                                "/dashboard/api/confirm-recovery", "/dashboard/api/discard-recovery").permitAll()
                        .anyRequest().hasAuthority(DashboardAccounts.AUTHORITY))
                .build();
    }

    static @Nullable String cookie(HttpServletRequest request, String name) {
        var cookie = WebUtils.getCookie(request, name);
        return cookie == null ? null : cookie.getValue();
    }

    static String oauthBrowser(HttpServletRequest request, HttpServletResponse response, String callback) {
        String browser = cookie(request, OAUTH_COOKIE);
        if (browser == null || !DashboardTokens.valid(browser)) {
            browser = DashboardTokens.create();
        }
        setCookie(response, OAUTH_COOKIE, browser, DiscordDashboardOAuth.ATTEMPT_LIFETIME,
                "https".equals(URI.create(callback).getScheme()));
        return browser;
    }

    static void setCookie(HttpServletResponse response, String name, String value, Duration age, boolean secure) {
        response.addHeader("Set-Cookie", ResponseCookie.from(name, value).path("/dashboard").httpOnly(true)
                .sameSite("Lax").secure(secure).maxAge(age).build().toString());
    }

    static ResponseEntity<Void> dashboard() {
        return redirect(URI.create("/dashboard/"));
    }

    static ResponseEntity<Void> dashboard(Outcome outcome) {
        return redirect(URI.create("/dashboard/?oauth=" + outcome.name().toLowerCase(Locale.ROOT)));
    }

    static ResponseEntity<Void> redirect(URI location) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(location).build();
    }

    enum Outcome { CONFIRM, EXPIRED, REAUTHENTICATE, RECOVERY_CONFIRM, RECOVERY_INVALID, RECOVERY_SIGNED_IN, REJECTED,
        UNAVAILABLE }
}
