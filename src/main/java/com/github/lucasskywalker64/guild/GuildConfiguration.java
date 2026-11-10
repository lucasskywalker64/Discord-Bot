package com.github.lucasskywalker64.guild;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "guild_configuration")
public class GuildConfiguration {

    @Id
    @Column(name = "guild_id", length = 20, nullable = false)
    private String guildId;

    @Column(name = "member_count_voice_channel_id", length = 20)
    private String memberCountVoiceChannelId;

    protected GuildConfiguration() {
    }

    public GuildConfiguration(String guildId, String memberCountVoiceChannelId) {
        this.guildId = guildId;
        this.memberCountVoiceChannelId = memberCountVoiceChannelId;
    }

    public String getGuildId() {
        return guildId;
    }

    public String getMemberCountVoiceChannelId() {
        return memberCountVoiceChannelId;
    }
}
