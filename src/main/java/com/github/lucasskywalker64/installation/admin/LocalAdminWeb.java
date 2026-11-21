package com.github.lucasskywalker64.installation.admin;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(LocalAdminWeb.Listener.class)
class LocalAdminWeb implements WebMvcConfigurer {
    private static final String PRIVATE_LISTENER = LocalAdminWeb.class.getName() + ".privateListener";
    private static final PathPatternRequestMatcher ADMIN_PATH =
            PathPatternRequestMatcher.withDefaults().matcher("/admin/**");

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> privateListener(Listener listener)
            throws UnknownHostException {
        var protocol = new Http11NioProtocol();
        protocol.setAddress(InetAddress.getByName(listener.address()));
        var connector = new Connector(protocol);
        connector.setPort(listener.port());
        connector.setThrowOnFailure(true);
        return factory -> {
            factory.addAdditionalConnectors(connector);
            factory.addEngineValves(new ValveBase(true) {
                @Override
                public void invoke(Request request, Response response) throws IOException, ServletException {
                    request.setAttribute(PRIVATE_LISTENER, request.getConnector() == connector);
                    getNext().invoke(request, response);
                }
            });
        };
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/admin", "/admin/");
        registry.addViewController("/admin/").setViewName("forward:/admin/index.html");
    }

    @Bean
    AuthenticationManager localAuthenticationManager(LocalAdministrator administrator) {
        return administrator::authenticate;
    }

    @Bean
    @Order(1)
    SecurityFilterChain localAdmin(HttpSecurity http, LocalAdministrator administrator,
                                   AuthenticationManager localAuthenticationManager) {
        var contexts = new HttpSessionSecurityContextRepository();
        contexts.setSpringSecurityContextKey("LOCAL_ADMIN_SECURITY_CONTEXT");
        return http.securityMatcher(request -> onPrivateListener(request) || ADMIN_PATH.matches(request))
                .addFilterBefore(LocalAdminWeb::requirePrivateRoute, SecurityContextHolderFilter.class)
                .addFilterAfter(credentialVersionFilter(administrator), AnonymousAuthenticationFilter.class)
                .authenticationManager(localAuthenticationManager)
                .securityContext(context -> context.securityContextRepository(contexts))
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((_, response, _) -> response.setStatus(HttpStatus.FORBIDDEN.value())))
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")))
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/admin", "/admin/", "/admin/index.html",
                                "/admin/assets/**", "/admin/api/session").permitAll()
                        .requestMatchers(HttpMethod.POST, "/admin/api/claim", "/admin/api/login").permitAll()
                        .anyRequest().hasAuthority(LocalAdministrator.AUTHORITY))
                .formLogin(login -> login.loginPage("/admin/").loginProcessingUrl("/admin/api/login")
                        .successHandler((_, response, _) -> response.setStatus(HttpStatus.NO_CONTENT.value()))
                        .failureHandler((_, response, error) -> response.setStatus(error instanceof LockedException
                                ? HttpStatus.TOO_MANY_REQUESTS.value() : HttpStatus.UNAUTHORIZED.value())))
                .logout(logout -> logout.logoutUrl("/admin/api/logout")
                        .logoutSuccessHandler((_, response, _) -> response.setStatus(HttpStatus.NO_CONTENT.value())))
                .build();
    }

    @Bean
    SecurityFilterChain publicHttp(HttpSecurity http) {
        return http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .build();
    }

    private static void requirePrivateRoute(ServletRequest request, ServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var http = (HttpServletRequest) request;
        if (onPrivateListener(http) && (ADMIN_PATH.matches(http) || isErrorPage(http))) {
            chain.doFilter(request, response);
        } else {
            ((HttpServletResponse) response).setStatus(HttpStatus.NOT_FOUND.value());
        }
    }

    private static OncePerRequestFilter credentialVersionFilter(LocalAdministrator administrator) {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication != null && authentication.getPrincipal()
                        instanceof LocalAdministrator.AdminPrincipal principal && !administrator.isCurrent(principal)) {
                    SecurityContextHolder.clearContext();
                    var session = request.getSession(false);
                    if (session != null) {
                        try {
                            session.invalidate();
                        } catch (IllegalStateException _) {
                            // Another request already invalidated this session.
                        }
                    }
                }
                chain.doFilter(request, response);
            }
        };
    }

    private static boolean onPrivateListener(HttpServletRequest request) {
        return Boolean.TRUE.equals(request.getAttribute(PRIVATE_LISTENER));
    }

    private static boolean isErrorPage(HttpServletRequest request) {
        return request.getDispatcherType() == DispatcherType.ERROR
                && request.getRequestURI().equals(request.getContextPath() + "/error");
    }

    @ConfigurationProperties("installation.admin")
    record Listener(@DefaultValue("127.0.0.1") String address, @DefaultValue("8081") int port) {
    }
}
