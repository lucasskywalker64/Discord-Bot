package com.github.lucasskywalker64.persistence.repository;

import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.persistence.data.YouTubeData;
import com.github.lucasskywalker64.persistence.PersistenceUtil;
import org.apache.commons.csv.CSVRecord;
import org.tinylog.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class YouTubeRepository {

    private static final Path FILE_PATH = BotMain.getYouTubeFile().toPath();
    private static final String[] HEADERS = {
            "channel", "message", "name", "playlistId", "roleId", "videoId", "streamId"
    };
    private final List<YouTubeData> localYouTubeData;

    public List<YouTubeData> loadAll() {
        return new ArrayList<>(localYouTubeData);
    }

    public void saveAll(List<YouTubeData> youTubeData) throws IOException {
        saveAll(youTubeData, true);
    }

    public void saveAll(List<YouTubeData> youtubeData, boolean append) throws IOException {
        if (!localYouTubeData.equals(youtubeData)) {
            PersistenceUtil.writeCsv(FILE_PATH, youtubeData, d -> new String[]{
                    d.channel(),
                    d.message(),
                    d.name(),
                    d.playlistId(),
                    d.roleId(),
                    d.videoId(),
                    d.streamId()
            }, append, HEADERS);
            if (!append)
                localYouTubeData.clear();
            localYouTubeData.addAll(youtubeData);
            Logger.info("YouTube data saved.");
        }
    }

    private YouTubeRepository() throws IOException {
        localYouTubeData = PersistenceUtil.readCsv(FILE_PATH, (CSVRecord record) -> new YouTubeData(
                record.get("channel"),
                record.get("message"),
                record.get("name"),
                record.get("playlistId"),
                record.get("roleId"),
                record.get("videoId"),
                record.get("streamId")), HEADERS);
        Logger.info("YouTube data loaded.");
    }

    private static class Holder {
        private static final YouTubeRepository INSTANCE;

        static {
            try {
                INSTANCE = new YouTubeRepository();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static YouTubeRepository getInstance() {
        return Holder.INSTANCE;
    }
}
