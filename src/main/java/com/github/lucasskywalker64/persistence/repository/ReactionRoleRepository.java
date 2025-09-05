package com.github.lucasskywalker64.persistence.repository;

import com.github.lucasskywalker64.BotMain;
import com.github.lucasskywalker64.persistence.PersistenceUtil;
import com.github.lucasskywalker64.persistence.data.ReactionRoleData;
import org.apache.commons.csv.CSVRecord;
import org.tinylog.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ReactionRoleRepository {

    private static final Path FILE_PATH = BotMain.getReactionRolesFile().toPath();
    private static final String[] HEADERS = {
            "channelId", "messageId", "roleId", "roleName", "emoji"
    };
    private final List<ReactionRoleData> localReactionRoleData;
    
    public List<ReactionRoleData> loadAll() {
        return new ArrayList<>(localReactionRoleData);
    }

    public void saveAll(List<ReactionRoleData> reactionRoleData) throws IOException {
        saveAll(reactionRoleData, true);
    }

    public void saveAll(List<ReactionRoleData> reactionRoleData, boolean append) throws IOException {
        if (!localReactionRoleData.equals(reactionRoleData)) {
            PersistenceUtil.writeCsv(FILE_PATH, reactionRoleData, d -> new String[]{
                    d.channelId(),
                    d.messageId(),
                    d.roleId(),
                    d.roleName(),
                    d.emoji()
            }, append, HEADERS);
            if (!append)
                localReactionRoleData.clear();
            localReactionRoleData.addAll(reactionRoleData);
            Logger.info("Reaction role data saved.");
        }
    }
    
    private ReactionRoleRepository() throws IOException {
        localReactionRoleData = PersistenceUtil.readCsv(FILE_PATH, (CSVRecord record) -> new ReactionRoleData(
                record.get("channelId"),
                record.get("messageId"),
                record.get("roleId"),
                record.get("roleName"),
                record.get("emoji")
        ));
        Logger.info("Reaction role data loaded.");
    }
    
    private static class Holder {
        private static final ReactionRoleRepository INSTANCE;

        static {
            try {
                INSTANCE = new ReactionRoleRepository();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    public static ReactionRoleRepository getInstance() {
        return Holder.INSTANCE;
    }
}
