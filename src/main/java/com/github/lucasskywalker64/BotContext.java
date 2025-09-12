package com.github.lucasskywalker64;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;

import java.io.File;

public record BotContext(JDA jda, Dotenv config, File botFile) {}
