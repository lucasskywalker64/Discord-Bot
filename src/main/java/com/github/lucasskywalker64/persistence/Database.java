package com.github.lucasskywalker64.persistence;

import com.github.lucasskywalker64.BotMain;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.tinylog.Logger;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;

public final class Database {

    private static final Path DB_PATH = Paths.get(BotMain.getContext().botFile().getParent(),
            "bot_files", "bot.db");
    private final HikariDataSource dataSource;

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    private void initSchema(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS youtube (" +
                    "channel TEXT NOT NULL, " +
                    "message TEXT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "playlistId TEXT NOT NULL PRIMARY KEY, " +
                    "roleId TEXT NOT NULL, " +
                    "videoId TEXT, " +
                    "streamId TEXT) ");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS twitch (" +
                    "channel TEXT NOT NULL, " +
                    "message TEXT NOT NULL, " +
                    "username TEXT NOT NULL PRIMARY KEY, " +
                    "roleId TEXT, " +
                    "announcementId TEXT, " +
                    "timestamp INTEGER NOT NULL, " +
                    "gameName TEXT, " +
                    "boxArtUrl TEXT) ");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS token_data (" +
                    "user_id INTEGER PRIMARY KEY," +
                    "login TEXT NOT NULL," +
                    "access_token TEXT NOT NULL," +
                    "refresh_token TEXT NOT NULL," +
                    "expires_at INTEGER NOT NULL)");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS shoutout (" +
                    "username TEXT PRIMARY KEY) ");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS shoutedout (" +
                    "name TEXT PRIMARY KEY) ");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS reaction_roles (" +
                    "channelId TEXT NOT NULL, " +
                    "messageId TEXT NOT NULL, " +
                    "roleId TEXT NOT NULL PRIMARY KEY, " +
                    "roleName TEXT NOT NULL, " +
                    "emoji TEXT NOT NULL) ");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS settings (" +
                    "key TEXT PRIMARY KEY, value TEXT NOT NULL) ");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS tickets (" +
                    "id INTEGER NOT NULL PRIMARY KEY, " +
                    "openerId INTEGER NOT NULL," +
                    "channelId INTEGER NOT NULL," +
                    "status TEXT NOT NULL," +
                    "createdAt TEXT NOT NULL," +
                    "closedAt TEXT," +
                    "closerId INTEGER," +
                    "reason TEXT," +
                    "claimerId INTEGER," +
                    "transcriptContent TEXT," +
                    "transcriptJson TEXT)");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS ticket_config (" +
                    "ticketsCategoryId INTEGER PRIMARY KEY," +
                    "supportRoleId INTEGER NOT NULL," +
                    "logChannelId INTEGER NOT NULL," +
                    "maxOpenTicketsPerUser INTEGER NOT NULL," +
                    "autoCloseAfter INTEGER NOT NULL)");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS ticket_authorized_users (" +
                    "ticketId INTEGER NOT NULL," +
                    "userId INTEGER NOT NULL," +
                    "FOREIGN KEY (ticketId) REFERENCES tickets (id) ON DELETE CASCADE," +
                    "PRIMARY KEY (ticketId, userId))");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS ticket_attachment_keys (" +
                    "ticketId INTEGER NOT NULL," +
                    "attachmentKey TEXT NOT NULL," +
                    "FOREIGN KEY (ticketId) REFERENCES tickets (id) ON DELETE CASCADE," +
                    "PRIMARY KEY (ticketId, attachmentKey))");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS scheduled_ticket_closures (" +
                    "ticketId INTEGER PRIMARY KEY," +
                    "closeTimeEpoch INTEGER NOT NULL," +
                    "channelId INTEGER NOT NULL)");
        }
    }

    public void shutdown() {
        if (dataSource != null) {
            dataSource.close();
            Logger.info("Database shutdown");
        }
    }

    private Database() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + DB_PATH.toAbsolutePath());
        config.addDataSourceProperty("journal_mode", "WAL");
        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(30000);

        this.dataSource = new HikariDataSource(config);

        try (Connection conn = getConnection()) {
            initSchema(conn);
        }
        Logger.info("Database initialized.");
    }

    private static class Holder {
        private static final Database INSTANCE;

        static {
            try {
                INSTANCE = new Database();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static Database getInstance() {
        return Holder.INSTANCE;
    }
}
