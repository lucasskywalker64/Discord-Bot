package com.github.lucasskywalker64.persistence;

import com.github.lucasskywalker64.BotMain;
import org.tinylog.Logger;

import java.nio.file.Path;
import java.sql.*;

public final class Database {

    private static final Path DB_PATH = BotMain.getDbFile().toPath();
    private Connection connection;

    public Connection getConnection() {
        return connection;
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
        }
    }

    public void shutdown() {
        if (connection != null) {
            try { connection.close(); } catch (SQLException ignored) {}
            connection = null;
        }
    }

    public Database() throws Exception {
        try {
            String url = "jdbc:sqlite:" + DB_PATH.toAbsolutePath();
            connection = DriverManager.getConnection(url);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
            }
            initSchema(connection);
            Logger.info("Database initialized");
        } catch (Exception e) {
            Logger.error("Failed to initialize SQLite connection", e);
            throw new Exception("Failed to initialize SQLite connection", e);
        }
    }
}
