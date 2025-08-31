package com.github.lucasskywalker64.persistence.repository;

import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.persistence.data.ShoutoutData;
import com.github.lucasskywalker64.persistence.data.TwitchData;
import com.github.lucasskywalker64.persistence.PersistenceUtil;
import org.apache.commons.csv.CSVRecord;
import org.tinylog.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TwitchRepository {

    private static final Path TWITCH_FILE_PATH = BotMain.getTwitchFile().toPath();
    private static final Path SHOUTOUT_FILE_PATH = BotMain.getShoutoutFile().toPath();
    private static final Path MODERATOR_FILE_PATH = BotMain.getModeratorFile().toPath();
    private static final String[] HEADERS = {
            "channel", "message", "username", "role", "announcementId", "timestamp"
    };
    private final List<TwitchData> localTwitchData;
    private final List<ShoutoutData> localShoutoutData;

    public List<TwitchData> loadAll() {
        return new ArrayList<>(localTwitchData);
    }

    public void saveAll(List<TwitchData> twitchData) throws IOException {
         saveAll(twitchData, true);
    }

    public void saveAll(List<TwitchData> twitchData, boolean append) throws IOException {
        if (!localTwitchData.equals(twitchData)) {
            PersistenceUtil.writeCsv(TWITCH_FILE_PATH, twitchData, d -> new String[]{
                    d.channel(),
                    d.message(),
                    d.username().toLowerCase(),
                    d.role(),
                    d.announcementId(),
                    String.valueOf(d.timestamp())
            }, append, HEADERS);
            if (!append)
                localTwitchData.clear();
            localTwitchData.addAll(twitchData);
            Logger.info("Twitch data saved.");
        }
    }

    public List<ShoutoutData> loadAllShoutout() {
        return new ArrayList<>(localShoutoutData);
    }

    public void saveAllShoutout(List<ShoutoutData> shoutoutData) throws IOException {
        saveAllShoutout(shoutoutData, true);
    }

    public void saveAllShoutout(List<ShoutoutData> shoutoutData, boolean append) throws IOException {
        if (!localShoutoutData.equals(shoutoutData)) {
            PersistenceUtil.writeCsv(SHOUTOUT_FILE_PATH, shoutoutData, d -> new String[]{
                    d.username().toLowerCase()
            }, append, "username");
            if (!append)
                localShoutoutData.clear();
            localShoutoutData.addAll(shoutoutData);
            Logger.info("Shoutout data saved.");
        }
    }

    public String readModeratorName() {
        try {
            return PersistenceUtil.readFileAsString(MODERATOR_FILE_PATH);
        } catch (IOException e) {
            return "";
        }
    }

    private TwitchRepository() throws IOException {
        localTwitchData = PersistenceUtil.readCsv(TWITCH_FILE_PATH, (CSVRecord record) -> new TwitchData(
                record.get("channel"),
                record.get("message"),
                record.get("username"),
                record.get("role").contains("@") ?
                        record.get("role").substring(record.get("role").indexOf("&") + 1,
                                record.get("role").lastIndexOf(">"))
                        : record.get("role"),
                null,
                0L), HEADERS);
        localShoutoutData = PersistenceUtil.readCsv(SHOUTOUT_FILE_PATH, (CSVRecord record) -> new ShoutoutData(
                record.get("username")
        ), "username");
        Logger.info("Twitch and shoutout data loaded.");
    }

    private static class Holder {
        private static final TwitchRepository INSTANCE;

        static {
            try {
                INSTANCE = new TwitchRepository();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static TwitchRepository getInstance() {
        return Holder.INSTANCE;
    }
}
