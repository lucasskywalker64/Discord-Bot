package com.github.lucasskywalker64.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.lucasskywalker64.BotContext;
import com.github.lucasskywalker64.BotInitializer;
import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.persistence.Database;
import com.github.lucasskywalker64.persistence.data.OverlayData;
import com.github.lucasskywalker64.persistence.data.SystemEvent;
import com.github.lucasskywalker64.persistence.data.TokenData;
import com.github.lucasskywalker64.persistence.data.YouTubeData;
import com.github.lucasskywalker64.persistence.repository.OverlayRepository;
import com.github.lucasskywalker64.persistence.repository.SystemEventRepository;
import com.github.lucasskywalker64.persistence.repository.YouTubeRepository;
import com.github.lucasskywalker64.ticket.model.Ticket;
import com.github.lucasskywalker64.ticket.persistence.TicketRepository;
import com.github.twitch4j.common.util.TypeConvert;
import com.github.twitch4j.eventsub.events.CustomRewardRedemptionAddEvent;
import com.github.twitch4j.helix.domain.CustomReward;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import io.github.cdimascio.dotenv.Dotenv;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.websocket.WsContext;
import jakarta.servlet.http.HttpSession;
import org.eclipse.jetty.ee10.servlet.SessionHandler;
import org.eclipse.jetty.http.HttpCookie.SameSite;
import org.eclipse.jetty.session.DatabaseAdaptor;
import org.eclipse.jetty.session.DefaultSessionCache;
import org.eclipse.jetty.session.JDBCSessionDataStoreFactory;
import org.eclipse.jetty.session.SessionCache;
import org.eclipse.jetty.websocket.api.Callback;
import org.jetbrains.annotations.Nullable;
import org.tinylog.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static io.javalin.apibuilder.ApiBuilder.*;

public class WebServer {

    private final String ENCODED_TWITCH_REDIRECT_URI;
    private final String TWITCH_EVENTSUB_SECRET;
    private final String TWITCH_CLIENT_ID;
    private final String TWITCH_SCOPES;
    private final String TWITCH_STREAMER_SCOPES;
    private final String ENCODED_DISCORD_REDIRECT_URI;
    private final String DISCORD_CLIENT_ID;
    private final String DISCORD_CLIENT_SECRET;
    private final int port;

    private final BotContext botContext;
    private final TicketRepository ticketRepository;
    private final YouTubeRepository youTubeRepository;
    private final OverlayRepository overlayRepository;
    private final SystemEventRepository systemEventRepository;

    private final HttpClient httpClient;
    private final PresignedUrlGenerator presignedUrlGenerator;
    private final Gson gson;
    private final ConcurrentHashMap<String, ExecutorService> channelExecutors;
    private final Map<String, Set<WsContext>> overlayClients;
    private final Map<String, Instant> userRateLimits;

    public WebServer() {
        botContext = BotMain.getContext();
        Dotenv config = botContext.config();
        String serverBaseUrl = config.get("SERVER_BASE_URL");
        port = Integer.parseInt(config.get("SERVER_PORT"));
        TWITCH_EVENTSUB_SECRET = config.get("TWITCH_EVENTSUB_SECRET");
        TWITCH_CLIENT_ID = config.get("TWITCH_CLIENT_ID");
        TWITCH_SCOPES = config.get("TWITCH_SCOPE");
        TWITCH_STREAMER_SCOPES = config.get("TWITCH_STREAMER_SCOPE");
        ENCODED_TWITCH_REDIRECT_URI = URLEncoder.encode(serverBaseUrl + Routes.AUTH_TWITCH_CALLBACK,
                StandardCharsets.UTF_8);
        ENCODED_DISCORD_REDIRECT_URI = URLEncoder.encode(serverBaseUrl + Routes.AUTH_DISCORD_CALLBACK,
                StandardCharsets.UTF_8);
        DISCORD_CLIENT_ID = config.get("DISCORD_CLIENT_ID");
        DISCORD_CLIENT_SECRET = config.get("DISCORD_CLIENT_SECRET");
        ticketRepository = TicketRepository.getInstance();
        youTubeRepository = YouTubeRepository.getInstance();
        overlayRepository = OverlayRepository.getInstance();
        systemEventRepository = SystemEventRepository.getInstance();
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        presignedUrlGenerator = new PresignedUrlGenerator();
        gson = new Gson();
        channelExecutors = new ConcurrentHashMap<>();
        overlayClients = new ConcurrentHashMap<>();
        userRateLimits = new ConcurrentHashMap<>();
    }

    public void start() {
        Javalin.create(config -> {
            config.concurrency.useVirtualThreads = true;
            config.requestLogger.http((ctx, ms) -> {
                if (!ctx.status().equals(HttpStatus.NOT_FOUND))
                    Logger.info("{} {} {} {}, User Agent: \"{}\" ({}ms)",
                            ctx.header("CF-Connecting-IP"),
                            ctx.method(),
                            ctx.path(),
                            ctx.status(),
                            ctx.userAgent() != null ? ctx.userAgent() : "-",
                            ms);
            });
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/public";
                staticFiles.directory = "/public";
            });

            config.jetty.modifyServletContextHandler(servletContextHandler -> {
                SessionHandler sessionHandler = new SessionHandler();
                sessionHandler.setHttpOnly(true);
                sessionHandler.setSecureRequestOnly(true);
                sessionHandler.setSameSite(SameSite.LAX);
                sessionHandler.setMaxCookieAge((int) TimeUnit.DAYS.toSeconds(90));
                sessionHandler.setMaxInactiveInterval((int) TimeUnit.DAYS.toSeconds(90));

                SessionCache sessionCache = new DefaultSessionCache(sessionHandler);

                JDBCSessionDataStoreFactory sessionStoreFactory = new JDBCSessionDataStoreFactory();
                DatabaseAdaptor databaseAdaptor = new DatabaseAdaptor();
                databaseAdaptor.setDatasource(Database.getInstance().getDataSource());
                databaseAdaptor.setDriverInfo("org.sqlite.JDBC", null);
                try {
                    databaseAdaptor.initialize();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                sessionStoreFactory.setDatabaseAdaptor(databaseAdaptor);

                sessionCache.setSessionDataStore(sessionStoreFactory.getSessionDataStore(sessionHandler));
                sessionHandler.setSessionCache(sessionCache);
                servletContextHandler.setSessionHandler(sessionHandler);
            });

            config.routes.exception(Exception.class, (e, ctx) -> {
                Logger.error(
                        "Unhandled exception for request: {} {} {}",
                        ctx.method(),
                        ctx.path(),
                        e
                );

                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("An internal server error occurred.");
            });
            config.routes.exception(AccessDeniedException.class, (e, ctx) -> Logger.warn(
                    "Authorization Failed (403): User at {} tried to access {}",
                    ctx.header("CF-Connecting-IP"),
                    ctx.path()
            ));

            config.routes.apiBuilder(() -> {
                path("api", () -> {
                    path("auth", () -> {
                        path("twitch", () -> {
                            get(this::handleTwitchLogin);
                            get("callback", this::handleTwitchCallback);
                        });
                        path("discord", () -> {
                            get(this::handleDiscordLogin);
                            get("callback", this::handleDiscordCallback);
                        });
                        path("dashboard", () -> {
                            get(this::dashboardAuthHandler);
                            post("logout", this::dashboardLogoutHandler);
                        });
                    });

                    path("dashboard", () -> {
                        get(this::dashboardHandler);
                        get("overlays", this::overlayListHandler);
                        post("overlays", this::overlayCreateHandler);
                        patch("overlays/{id}", this::overlayUpdateHandler);
                        delete("overlays/{id}", this::overlayDeleteHandler);
                        post("overlays/{id}/regenerate", this::overlayRegenerateHandler);
                    });

                    path("twitch", () -> get("rewards", this::twitchRewardsHandler));

                    path("webhooks", () -> {
                        post("twitch", this::handleTwitchEventsub);
                        path("youtube", () -> {
                            get(this::youTubeChallengeHandler);
                            post(this::youTubeWebhookHandler);
                        });
                    });

                    path("tickets", () -> {
                        get(this::handleTicketList);
                        path("transcripts/{id}", () -> {
                            get(this::handleTranscriptRequest);
                            get("attachments", this::handleTranscriptAttachments);
                        });
                    });
                });

                ws(Routes.WEBSOCKET_OVERLAY, ws -> {
                    ws.onConnect(ctx -> {
                        String token = ctx.pathParam("token");

                        Optional<OverlayData> overlayOptional;
                        try {
                            overlayOptional = overlayRepository.getOverlayByToken(token);
                        } catch (SQLException e) {
                            Logger.error(e, "Failed to get overlay data");
                            ctx.session.close(1008, "Internal Server Error", Callback.NOOP);
                            return;
                        }

                        if (overlayOptional.isEmpty()) {
                            ctx.session.close(1008, "Invalid Token", Callback.NOOP);
                            return;
                        }

                        OverlayData overlayData = overlayOptional.get();

                        String streamerId = overlayData.streamerId();
                        ctx.attribute("streamerId", streamerId);
                        ctx.attribute("overlayId", overlayData.id());

                        overlayClients.computeIfAbsent(streamerId, k -> ConcurrentHashMap.newKeySet()).add(ctx);
                        Logger.info("Overlay connected for streamer ID: {}", streamerId);

                        List<SystemEvent> backlogEvents = systemEventRepository.getUnfulfilledTwitchRedemptions(streamerId);

                        StringBuilder eventsJsonBuilder = new StringBuilder("[");
                        for (int i = 0; i < backlogEvents.size(); i++) {
                            SystemEvent event = backlogEvents.get(i);

                            JsonNode rootNode = TypeConvert.getObjectMapper().readTree(event.payload());
                            CustomRewardRedemptionAddEvent twitchEvent = TypeConvert.getObjectMapper()
                                    .treeToValue(rootNode.get("event"), CustomRewardRedemptionAddEvent.class);

                            String formattedEvent = """
                            {
                                "type": "add",
                                "id": "%s",
                                "user": "%s",
                                "reward": "%s"
                            }
                            """.formatted(
                                    twitchEvent.getId(),
                                    twitchEvent.getUserName(),
                                    twitchEvent.getReward().getTitle()
                            );

                            eventsJsonBuilder.append(formattedEvent);
                            if (i < backlogEvents.size() - 1)
                                eventsJsonBuilder.append(",");
                        }
                        eventsJsonBuilder.append("]");

                        ctx.send("""
                        {
                            "type": "init_overlay",
                            "settings": %s,
                            "events": %s
                        }
                        """
                                .formatted(overlayData.settings(), eventsJsonBuilder.toString())
                        );
                    });

                    ws.onClose(ctx -> {
                        String streamerId = ctx.attribute("streamerId");
                        if (streamerId != null) {
                            Set<WsContext> clients = overlayClients.get(streamerId);
                            if (clients != null) {
                                clients.remove(ctx);
                                if (clients.isEmpty())
                                    overlayClients.remove(streamerId);
                            }
                            Logger.info("Overlay disconnected for streamer ID: {}", streamerId);
                        }
                    });

                    ws.onError(ctx -> {
                        Logger.error(ctx.error(), "WebSocket error");
                        if (ctx.session.isOpen())
                            ctx.session.close(1011, "Internal Server Error", Callback.NOOP);
                    });

                    ws.onMessage(ctx -> {});
                });
            });

            config.events.serverStarted(() -> Logger.info("Webserver is ready"));
        }).start(port);
    }

    public void broadcastToOverlays(String broadcasterId, String jsonPayload) {
        Set<WsContext> clients = overlayClients.get(broadcasterId);
        if (clients != null) {
            clients.stream()
                    .filter(ctx -> ctx.session.isOpen())
                    .forEach(ctx -> ctx.send(jsonPayload));
        }
    }

    private void dashboardHandler(Context ctx) {
        String twitchLogin = ctx.sessionAttribute("twitch_login");
        String twitchUserId = ctx.sessionAttribute("twitch_user_id");
        String twitchProfileImageUrl = ctx.sessionAttribute("twitch_profile_image_url");

        if (twitchLogin == null || twitchUserId == null) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "Not logged in"));
            return;
        }

        String token;
        try {
            String signature = computeHmacSha1(twitchUserId.getBytes(StandardCharsets.UTF_8), TWITCH_EVENTSUB_SECRET);
            token = twitchUserId + "-" + signature;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            Logger.error(e, "Failed to generate overlay token");
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", "Failed to generate overlay token"));
            return;
        }

        ctx.json(Map.of(
                "login", twitchLogin,
                "token", token,
                "profileImageUrl", twitchProfileImageUrl != null ? twitchProfileImageUrl : ""
        ));
    }

    private void dashboardAuthHandler(Context ctx) {
        String redirectPath = ctx.queryParam("redirect_path");
        if (redirectPath != null) {
            ctx.sessionAttribute("redirect_path", redirectPath);
        }

        String state = UUID.randomUUID().toString();
        ctx.sessionAttribute("state", new ExpiringSessionAttribute(state, TimeUnit.MINUTES.toSeconds(10)));
        ctx.sessionAttribute("streamer", true);

        String twitchAuthUrl = """
                https://id.twitch.tv/oauth2/authorize?
                client_id=%s&
                redirect_uri=%s&
                response_type=code&
                state=%s
                """
                .formatted(
                        TWITCH_CLIENT_ID,
                        ENCODED_TWITCH_REDIRECT_URI,
                        state
                );

        ctx.json(Map.of("authUrl", twitchAuthUrl));
    }

    private void dashboardLogoutHandler(Context ctx) {
        HttpSession session = ctx.req().getSession(false);
        if (session != null) {
            session.invalidate();
        } else {
            Logger.warn("Session not found for logout request");
        }
        ctx.removeCookie("JSESSIONID");
        ctx.status(HttpStatus.OK).json(Map.of("message", "Successfully logged out"));
    }

    private void overlayListHandler(Context ctx) {
        String twitchUserId = ctx.sessionAttribute("twitch_user_id");
        if (twitchUserId == null) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "Not logged in"));
            return;
        }

        List<OverlayData> overlays;
        try {
            overlays = overlayRepository.getByStreamerId(twitchUserId);
        } catch (SQLException e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", "Failed to fetch overlays"));
            return;
        }

        ctx.json(overlays);
    }

    private void overlayCreateHandler(Context ctx) {
        String twitchUserId = validateUserAndRateLimit(ctx, 3);
        if (twitchUserId == null) return;

        OverlaySaveRequest request = parseOverlayRequest(ctx);
        if (request == null) return;

        if (request.type() == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "Invalid request"));
            return;
        }

        String instanceId = UUID.randomUUID().toString();
        String token = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");

        String settingsString = extractSettingsJson(request);
        OverlayData overlayToSave = new OverlayData(
                instanceId,
                twitchUserId,
                request.type(),
                token,
                settingsString
        );

        try {
            overlayRepository.createOverlay(overlayToSave);
        } catch (SQLException e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", "Failed to save overlay"));
            return;
        }

        ctx.status(HttpStatus.OK).json(overlayToSave);
    }

    private void overlayUpdateHandler(Context ctx) {
        String twitchUserId = validateUserAndRateLimit(ctx, 3);
        if (twitchUserId == null) return;

        OverlaySaveRequest request = parseOverlayRequest(ctx);
        if (request == null) return;
        
        String id = ctx.pathParam("id");
        String settingsString = extractSettingsJson(request);
        
        try {
            if (overlayRepository.updateOverlaySettings(id, twitchUserId, settingsString))
                ctx.status(HttpStatus.OK).json(Map.of("message", "Overlay updated successfully"));
            else
                ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "Overlay not found"));
        } catch (SQLException e) {
            Logger.error(e, "Failed to update overlay");
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", "Failed to update overlay"));
        }
    }

    private void overlayDeleteHandler(Context ctx) {
        String twitchUserId = validateUserAndRateLimit(ctx, 3);
        if (twitchUserId == null) return;

        String id = ctx.pathParam("id");

        try {
            if (overlayRepository.deleteOverlay(id, twitchUserId))
                ctx.status(HttpStatus.OK).json(Map.of("message", "Overlay deleted successfully"));
            else
                ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "Overlay not found"));
        } catch (SQLException e) {
            Logger.error(e, "Failed to delete overlay");
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", "Failed to delete overlay"));
        }
    }

    private void overlayRegenerateHandler(Context ctx) {
        String twitchUserId = validateUserAndRateLimit(ctx, 5);
        if (twitchUserId == null) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "Not logged in"));
        }

        String id = ctx.pathParam("id");

        List<OverlayData> userOverlays;
        try {
            userOverlays = overlayRepository.getByStreamerId(twitchUserId);
        } catch (SQLException e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", "Failed to fetch overlays"));
            return;
        }

        boolean ownsOverlay = userOverlays.stream()
                .anyMatch(o -> o.id().equals(id));

        if (!ownsOverlay) {
            ctx.status(HttpStatus.FORBIDDEN).json(Map.of("error", "You do not own this overlay"));
        }

        String newToken;
        try {
            newToken = overlayRepository.regenerateToken(id);
        } catch (SQLException e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", "Failed to regenerate overlay token"));
            return;
        }

        ctx.status(HttpStatus.OK).json(Map.of("token", newToken));
    }

    private void handleTwitchLogin(Context ctx) {
        String state = ctx.queryParam("state");
        if (state == null || !botContext.twitchOauthService().consumeAuthState(state)) {
            Logger.warn("Twitch login state mismatch: {}", state);
            ctx.status(HttpStatus.FORBIDDEN).result("Invalid or expired session. Please try again.");
            return;
        }
        ctx.sessionAttribute("state", new ExpiringSessionAttribute(state, TimeUnit.MINUTES.toSeconds(10)));

        if (ctx.queryParam("streamer") == null) {
            ctx.status(HttpStatus.BAD_REQUEST).html("<h1>400 Bad Request</h1>" +
                    "<p>Missing streamer parameter.</p>");
            return;
        }
        boolean streamerLogin = Boolean.parseBoolean(ctx.queryParam("streamer"));
        ctx.sessionAttribute("streamer", streamerLogin);

        String redirectPath = ctx.queryParam("redirect_path");
        if (redirectPath != null) {
            ctx.sessionAttribute("redirect_path", redirectPath);
        }
        Logger.info(Optional.ofNullable(ctx.sessionAttribute("redirect_path")));

        String twitchAuthUrl = "https://id.twitch.tv/oauth2/authorize" +
                "?client_id=" + TWITCH_CLIENT_ID +
                "&redirect_uri=" + ENCODED_TWITCH_REDIRECT_URI +
                "&response_type=code" +
                "&scope=" + URLEncoder.encode(streamerLogin ? TWITCH_STREAMER_SCOPES : TWITCH_SCOPES,
                StandardCharsets.UTF_8) +
                "&state=" + state;
        ctx.redirect(twitchAuthUrl);
    }

    private void handleTwitchCallback(Context ctx) {
        if (stateIsInvalid(ctx))
            return;

        String code = ctx.queryParam("code");

        if (code == null) {
            ctx.status(400).html("<h1>Error: Authorization failed.</h1>");
            return;
        }

        try {
            if (ctx.sessionAttribute("streamer") == null) {
                ctx.status(HttpStatus.BAD_REQUEST).html("<h1>400 Bad Request</h1>" +
                        "<p>Missing streamer attribute.</p>");
                return;
            }

            boolean streamerLogin = Boolean.TRUE.equals(ctx.sessionAttribute("streamer"));
            TokenData tokenData = botContext.twitchOauthService().onOAuthCallback(code, streamerLogin);
            ctx.sessionAttribute("twitch_login", tokenData.login());
            ctx.sessionAttribute("twitch_user_id", tokenData.userId());

            try {
                HttpRequest profileRequest = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.twitch.tv/helix/users"))
                        .header("Authorization", "Bearer " + tokenData.bundle().accessToken())
                        .header("Client-Id", TWITCH_CLIENT_ID)
                        .GET()
                        .build();

                HttpResponse<String> profileResponse = httpClient.send(profileRequest, HttpResponse.BodyHandlers.ofString());

                if (profileResponse.statusCode() == 200) {
                    JsonNode rootNode = TypeConvert.getObjectMapper().readTree(profileResponse.body());
                    JsonNode dataArray = rootNode.path("data");

                    if (dataArray.isArray() && !dataArray.isEmpty()) {
                        String profileImageUrl = dataArray.get(0).path("profile_image_url").asText();
                        ctx.sessionAttribute("twitch_profile_image_url", profileImageUrl);
                        Logger.info("Successfully fetched profile picture for {}", tokenData.login());
                    }
                } else {
                    Logger.warn("Failed to fetch Twitch profile picture. Status: {}", profileResponse.statusCode());
                }
            } catch (Exception e) {
                Logger.warn(e, "Error occurred while fetching Twitch profile picture manually.");
            }

            if (botContext.twitch() == null) {
                BotInitializer.tryTwitchStart();
            }

            String redirectPath = ctx.sessionAttribute("redirect_path");
            if (redirectPath != null) {
                ctx.sessionAttribute("redirect_path", null);
                ctx.redirect(redirectPath);
                return;
            }
        } catch (Exception e) {
            Logger.error(e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).html("<h1>500 Internal Server Error</h1>" +
                    "<p>Please try again. If this error persists contact the developer.</p>");
            return;
        }
        ctx.status(HttpStatus.OK).html("<h1>Authorization successful</h1>" +
                "<p>You can now leave this page.</p>");
    }

    private void twitchRewardsHandler(Context ctx) {
        String twitchUserId = ctx.sessionAttribute("twitch_user_id");
        if (twitchUserId == null) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "Not logged in"));
            return;
        }

        try {
            List<CustomReward> rewards = botContext.twitchRedeemCacheService().getOrFetch(twitchUserId);

            List<Map<String, Object>> mappedRewards = rewards.stream().map(r -> {
                        String imageUrl = r.getImage() != null ?
                                r.getImage().getUrl1x() :
                                r.getDefaultImage() != null ?
                                        r.getDefaultImage().getUrl1x() : "";

                        return Map.<String, Object>of(
                                "id", r.getId(),
                                "title", r.getTitle(),
                                "cost", r.getCost(),
                                "image", imageUrl
                        );
                    }).toList();

            ctx.json(mappedRewards);
        } catch (Exception e) {
            Logger.error(e, "Failed to fetch Twitch rewards for user {}: ", twitchUserId);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", "Failed to load rewards"));
        }
    }

    private void handleTwitchEventsub(Context ctx) {
        Logger.info("Received Twitch EventSub request.");
        String messageId = ctx.header("Twitch-Eventsub-Message-Id");
        String timestamp = ctx.header("Twitch-Eventsub-Message-Timestamp");
        String signature = ctx.header("Twitch-Eventsub-Message-Signature");
        String messageType = ctx.header("Twitch-Eventsub-Message-Type");

        if (messageId == null || timestamp == null || signature == null || messageType == null) {
            Logger.warn("Missing required headers in Twitch EventSub request. Headers: {}, {}, {}, {}",
                    messageId, timestamp, signature, messageType);
            ctx.status(HttpStatus.BAD_REQUEST);
            return;
        }

        try {
            Instant messageTime = Instant.parse(timestamp);
            if (messageTime.isBefore(Instant.now().minus(Duration.ofMinutes(10)))) {
                Logger.warn("Received an expired Twitch Webhook message (Possible replay attack). Dropping.");
                ctx.status(HttpStatus.OK).result("");
                return;
            }
        } catch (Exception e) {
            Logger.error(e);
            ctx.status(HttpStatus.BAD_REQUEST);
            return;
        }

        try {
            if (systemEventRepository.eventExists("twitch", messageId)) {
                Logger.info("Duplicate Twitch EventSub message received. Ignoring.");
                ctx.status(HttpStatus.OK).result("");
                return;
            }
        } catch (SQLException e) {
            Logger.error(e, "Database error during deduplication check");
            ctx.status(HttpStatus.OK).result("");
            return;
        }

        String rawBody = ctx.body();
        if (!verifyTwitchSignature(signature, messageId, timestamp, rawBody)) {
            Logger.warn("Invalid Twitch EventSub signature from IP: {}", ctx.header("CF-Connecting-IP"));
            ctx.status(HttpStatus.FORBIDDEN);
            return;
        }

        try {
            JsonNode jsonNode = TypeConvert.getObjectMapper().readTree(rawBody);

            switch (messageType) {
                case "webhook_callback_verification" -> {
                    String challenge = jsonNode.get("challenge").asText();
                    ctx.contentType("text/plain").result(challenge).status(HttpStatus.OK);
                    Logger.info("Successfully verified Twitch EventSub webhook.");
                }
                case "notification" -> {
                    ctx.status(HttpStatus.OK).result("");

                    String eventType = jsonNode.path("subscription").path("type").asText("unknown-type");
                    String streamerId = jsonNode.path("subscription").path("condition")
                            .path("broadcaster_user_id").asText("unknown-streamer");
                    String referenceId = jsonNode.path("event").path("id").asText("unknown-ref");

                    SystemEvent event = new SystemEvent(
                            messageId, referenceId, streamerId, "twitch", eventType, "PENDING", rawBody
                    );

                    try {
                        systemEventRepository.saveEvent(event);
                    } catch (SQLException e) {
                        Logger.error(e, "Failed to save Twitch EventSub event to system queue");
                    }
                }
                case "revocation" -> {
                    ctx.status(HttpStatus.OK).result("");
                    Logger.warn("Twitch revoked webhook subscription: {}",
                            jsonNode.get("subscription").toPrettyString());
                }
                default -> ctx.status(HttpStatus.OK).result("");
            }
        } catch (JsonProcessingException e) {
            Logger.error(e, "Error processing Twitch EventSub webhook");
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void handleDiscordLogin(Context ctx) {
        String state = UUID.randomUUID().toString();
        ctx.sessionAttribute("state", new ExpiringSessionAttribute(state, TimeUnit.MINUTES.toSeconds(10)));

        String redirectPath = ctx.queryParam("redirect_path");
        if (redirectPath != null) {
            ctx.sessionAttribute("redirect_path", redirectPath);
        }

        String discordAuthUrl = "https://discord.com/oauth2/authorize?client_id=" + DISCORD_CLIENT_ID +
                "&redirect_uri=" + ENCODED_DISCORD_REDIRECT_URI +
                "&response_type=code&scope=identify" +
                "&state=" + state;
        ctx.redirect(discordAuthUrl);
    }

    private void handleDiscordCallback(Context ctx) throws IOException, InterruptedException {
        if (stateIsInvalid(ctx))
            return;

        String code = ctx.queryParam("code");

        if (code == null) {
            ctx.status(400).html("<h1>Error: Authorization failed.</h1>");
            return;
        }

        String body =
                "client_id=" + DISCORD_CLIENT_ID +
                "&client_secret=" + DISCORD_CLIENT_SECRET +
                "&grant_type=authorization_code" +
                "&code=" + code +
                "&redirect_uri=" + ENCODED_DISCORD_REDIRECT_URI;

        HttpRequest tokenRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://discord.com/api/v10/oauth2/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> tokenResponse = httpClient.send(tokenRequest, BodyHandlers.ofString());
        if (tokenResponse.statusCode() != 200) {
            Logger.error("Failed to get Discord access token: {}", tokenResponse.body());
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).html("<h1>Error: Could not verify with Discord.</h1>");
            return;
        }

        DiscordTokenResponse tokenData = gson.fromJson(tokenResponse.body(), DiscordTokenResponse.class);
        ctx.sessionAttribute("access_token", tokenData.accessToken);
        ctx.sessionAttribute("refresh_token", tokenData.refreshToken);

        String redirectPath = ctx.sessionAttribute("redirect_path");
        if (redirectPath != null) {
            ctx.sessionAttribute("redirect_path", null);
            ctx.redirect(redirectPath);
        } else ctx.html("<h1>Login successful!</h1>");
    }

    private static boolean stateIsInvalid(Context ctx) {
        String state = ctx.queryParam("state");
        ExpiringSessionAttribute expectedState = ctx.sessionAttribute("state");
        ctx.req().getSession().removeAttribute("state");

        if (state == null || expectedState == null) {
            ctx.status(HttpStatus.BAD_REQUEST).html("<h1>400 Bad Request</h1>" +
                    "<p>Missing state parameter.</p>");
            return true;
        }

        if (expectedState.isExpired()) {
            ctx.status(HttpStatus.BAD_REQUEST).html("<h1>400 Bad Request</h1>" +
                    "<p>Login flow expired. Please try again.</p>");
            return true;
        }

        if (!state.equals(expectedState.value)) {
            ctx.status(HttpStatus.BAD_REQUEST).html("<h1>400 Bad Request</h1>" +
                    "<p>State mismatch: Invalid state parameter.</p>");
            return true;
        }
        return false;
    }

    private DiscordTokenResponse refreshDiscordToken(String refreshToken) throws IOException, InterruptedException {
        if (refreshToken == null || refreshToken.isEmpty()) {
            return null;
        }

        String body = "client_id=" + DISCORD_CLIENT_ID +
                "&client_secret=" + DISCORD_CLIENT_SECRET +
                "&grant_type=refresh_token" +
                "&refresh_token=" + refreshToken;

        HttpRequest refreshRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://discord.com/api/v10/oauth2/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(refreshRequest, BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            Logger.info("Successfully refreshed Discord token.");
            return gson.fromJson(response.body(), DiscordTokenResponse.class);
        } else {
            Logger.warn("Failed to refresh Discord token. Status: {}, Body: {}", response.statusCode(), response.body());
            return null;
        }
    }

    private void handleTicketList(Context ctx) throws IOException, InterruptedException, SQLException {
        String guildId = ctx.queryParam("guild_id");
        if (guildId == null || guildId.isEmpty()) {
            ctx.status(HttpStatus.BAD_REQUEST).html("<h1>400 Bad Request</h1><p>Guild ID missing</p>");
        }

        DiscordUser discordUser = authorizeDiscordUser(ctx);
        if (discordUser == null) return;

        List<Ticket> tickets = ticketRepository.findByOpenerId(discordUser.id);
        String html = botContext.ticketModule().getService().listTicketsHtml(tickets, guildId);
        if (html == null) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).html("<h1>500 Internal Server Error</h1>" +
                    "<p>If this error persists, please contact the developer.</p>");
            return;
        }

        ctx.html(html);
    }

    private void handleTranscriptRequest(Context ctx) throws SQLException, IOException, InterruptedException {
        DiscordUser discordUser = authorizeDiscordUser(ctx);
        if (discordUser == null) return;

        Ticket ticket = getTicket(ctx, discordUser);
        if (ticket == null) return;

        String transcriptContent = ticket.transcriptContent();
        if (transcriptContent == null || transcriptContent.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND).html("<h1>404 Not Found</h1><p>Transcript content is not available.</p>");
            return;
        }

        ctx.html(transcriptContent);
    }

    private void handleTranscriptAttachments(Context ctx) throws IOException, InterruptedException, SQLException {
        DiscordUser discordUser = authorizeDiscordUser(ctx);
        if (discordUser == null) return;

        Ticket ticket = getTicket(ctx, discordUser);
        if (ticket == null) return;

        List<String> attachmentKeys = ticketRepository.getAttachmentKeysForTicket(ticket.id());
        Map<String, String> presignedUrls = new HashMap<>();
        for (String attachmentKey : attachmentKeys) {
            String url = presignedUrlGenerator.createPresignedUrl(attachmentKey);
            presignedUrls.put(attachmentKey, url);
        }
        ctx.json(presignedUrls);
    }

    private void youTubeChallengeHandler(Context ctx) {
        String challenge = ctx.queryParam("hub.challenge");
        String token = ctx.queryParam("token");

        if (challenge == null) {
            ctx.status(HttpStatus.NOT_FOUND).result("No challenge parameter found.");
            return;
        }
        ctx.status(HttpStatus.OK).result(challenge);

        CompletableFuture<Integer> future = null;
        if (token != null) {
            future = botContext.pendingChallenges().get(token);
        }


        if (future == null) {
            Logger.warn("Received challenge for an unknown or timed-out token: " + token);
            return;
        }

        String leaseSecondsStr = ctx.queryParam("hub.lease_seconds");
        String hubMode = ctx.queryParam("hub.mode");

        if (hubMode == null) {
            future.completeExceptionally(new RuntimeException("No hub.mode provided."));
            return;
        }

        if ("subscribe".equals(hubMode)) {
            if (leaseSecondsStr == null || leaseSecondsStr.isEmpty()) {
                future.completeExceptionally(new RuntimeException("No hub.lease_seconds provided."));
                return;
            }
            try {
                future.complete(Integer.parseInt(leaseSecondsStr));
            } catch (NumberFormatException e) {
                future.completeExceptionally(e);
            }
        } else {
            future.complete(0);
        }
    }

    private void youTubeWebhookHandler(Context ctx) {
        String hubSignature = ctx.header("X-Hub-Signature");
        String channelId = ctx.queryParam("channel_id");
        String guildId = ctx.queryParam("guild_id");
        if (hubSignature == null) {
            Logger.info("No secret");
            ctx.status(HttpStatus.BAD_REQUEST);
            return;
        }

        byte[] requestBodyBytes = ctx.bodyAsBytes();
        try {
            YouTubeData data = youTubeRepository.get(channelId, guildId);
            if (data == null) {
                Logger.info("No youtube data");
                ctx.status(HttpStatus.BAD_REQUEST);
                return;
            }

            String expectedSignature = "sha1=" + computeHmacSha1(requestBodyBytes, data.secret());
            if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8),
                    hubSignature.getBytes(StandardCharsets.UTF_8))) {
                Logger.info("Wrong signature");
                ctx.status(HttpStatus.BAD_REQUEST);
                return;
            }
            ctx.status(HttpStatus.NO_CONTENT);

            String executorKey = channelId + ":" + guildId;
            channelExecutors.computeIfAbsent(executorKey, key -> Executors.newSingleThreadExecutor())
                    .submit(() -> botContext.youTube()
                            .processNotification(new String(requestBodyBytes, StandardCharsets.UTF_8), data));
        } catch (InvalidKeyException | SQLException | NoSuchAlgorithmException e) {
            Logger.error(e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).html("<h1>500 Internal Server Error</h1>" +
                    "<p>Please try again. If this error persists contact the developer.</p>");
        }
    }

    private boolean verifyTwitchSignature(
            String signatureHeader,
            String messageId,
            String timestamp,
            String rawBody) {
        try {
            String messageToSign = messageId + timestamp + rawBody;

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(TWITCH_EVENTSUB_SECRET.getBytes(), "HmacSHA256");
            mac.init(secretKey);

            byte[] rawHmac = mac.doFinal(messageToSign.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder("sha256=");
            for (byte b : rawHmac) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return MessageDigest.isEqual(
                    hexString.toString().getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8)
            );
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            Logger.error(e, "Failed to verify Twitch signature");
            return false;
        }
    }

    private String computeHmacSha1(byte[] data, String key) throws NoSuchAlgorithmException, InvalidKeyException {
        SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), "HmacSHA1");
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(signingKey);
        byte[] rawHmac = mac.doFinal(data);

        Formatter formatter = new Formatter();
        for (byte b : rawHmac) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    private DiscordUser authorizeDiscordUser(Context ctx) throws IOException, InterruptedException {
        String accessToken = ctx.sessionAttribute("access_token");
        if (accessToken == null) {
            ctx.redirect(Routes.AUTH_DISCORD + "?redirect_path=" + ctx.path());
            return null;
        }

        DiscordUser discordUser = getDiscordUserFromToken(accessToken);
        if (discordUser == null) {
            Logger.info("Access token likely invalid, attempting refresh...");
            DiscordTokenResponse newTokens = refreshDiscordToken(ctx.sessionAttribute("refresh_token"));
            if (newTokens != null) {
                ctx.sessionAttribute("access_token", newTokens.accessToken);
                ctx.sessionAttribute("refresh_token", newTokens.refreshToken);
                discordUser = getDiscordUserFromToken(newTokens.accessToken);
            }
        }

        if (discordUser == null) {
            ctx.sessionAttribute("access_token", null);
            ctx.sessionAttribute("refresh_token", null);
            ctx.redirect(Routes.AUTH_DISCORD + "?redirect_path=" + ctx.path());
            return null;
        }

        return discordUser;
    }

    private @Nullable Ticket getTicket(Context ctx, DiscordUser discordUser) throws SQLException {
        long userId = discordUser.id;

        long ticketId;
        try {
            ticketId = Long.parseLong(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            ctx.status(HttpStatus.BAD_REQUEST).html("<h1>400 Bad Request</h1><p>Ticket ID must be numerical</p>");
            return null;
        }

        Ticket ticket = ticketRepository.findById(ticketId);
        if (ticket == null) {
            ctx.status(HttpStatus.NOT_FOUND).html("<h1>404 Not Found</h1>" +
                    "<p>The requested transcript does not exist</p>");
            return null;
        }

        List<Long> authorizedUsers = ticketRepository.getAuthorizedUsers(ticket.id());
        if (!authorizedUsers.contains(userId)) {
            ctx.status(HttpStatus.FORBIDDEN).html("<h1>403 Forbidden</h1>" +
                    "<p>You do not have permission to access this transcript.</p>");
            return null;
        }
        return ticket;
    }

    private DiscordUser getDiscordUserFromToken(String accessToken) throws IOException, InterruptedException {
        HttpRequest userRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://discord.com/api/v10/users/@me"))
                .header("Authorization", "Bearer " + accessToken)
                .build();

        HttpResponse<String> userResponse = httpClient.send(userRequest, BodyHandlers.ofString());
        if (userResponse.statusCode() != 200) {
            Logger.warn("Failed to get user info, token may be invalid. Status: {}, Body: {}",
                    userResponse.statusCode(), userResponse.body());
            return null;
        }
        return gson.fromJson(userResponse.body(), DiscordUser.class);
    }

    private String validateUserAndRateLimit(Context ctx, int rateLimitSeconds) {
        String twitchUserId = ctx.sessionAttribute("twitch_user_id");
        if (twitchUserId == null) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "Not logged in"));
            return null;
        }

        if (isRateLimited(twitchUserId, rateLimitSeconds)) {
            ctx.status(HttpStatus.TOO_MANY_REQUESTS).json(Map.of("error", "Rate limit exceeded"));
            return null;
        }

        return twitchUserId;
    }

    private boolean isRateLimited(String userId, long minSecondsBetweenRequests) {
        Instant now = Instant.now();
        Instant lastRequest = userRateLimits.get(userId);

        if (lastRequest != null && Duration.between(lastRequest, now).toSeconds() < minSecondsBetweenRequests)
            return true;

        userRateLimits.put(userId, now);
        return false;
    }

    private OverlaySaveRequest parseOverlayRequest(Context ctx) {
        try {
            return gson.fromJson(ctx.body(), OverlaySaveRequest.class);
        } catch (JsonSyntaxException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "Invalid JSON"));
            return null;
        }
    }

    private String extractSettingsJson(OverlaySaveRequest request) {
        return request.settings() != null ? gson.toJson(request.settings()) : "{}";
    }

    private record ExpiringSessionAttribute(String value, long expiryTimestamp) {
        public ExpiringSessionAttribute(String value, long expiryTimestamp) {
            this.value = value;
            this.expiryTimestamp = Instant.now().getEpochSecond() + expiryTimestamp;
        }

        public boolean isExpired() {
                return Instant.now().getEpochSecond() > expiryTimestamp;
        }
    }

    private record OverlaySaveRequest(String id, String type, Object settings) {}

    private record DiscordTokenResponse(
            @SerializedName("access_token") String accessToken,
            @SerializedName("refresh_token") String refreshToken
    ) {}

    private record DiscordUser(long id, String username) {}
}
