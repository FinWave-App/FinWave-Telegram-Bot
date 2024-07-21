package app.finwave.telegrambot.config;

import app.finwave.scw.RootConfig;
import com.google.inject.Singleton;

import java.io.File;
import java.io.IOException;

public class ConfigWorker {
    public final DatabaseConfig database;
    public final TelegramConfig telegram;
    public final OpenAIConfig openAI;
    public final CommonConfig commonConfig;
    public final LoggingConfig loggingConfig;

    protected final RootConfig main;

    public ConfigWorker() throws IOException {
        main = new RootConfig(new File("configs/main.conf"), true);
        main.load();

        database = main.subNode("database").getOrSetAs(DatabaseConfig.class, DatabaseConfig::new);
        telegram = main.subNode("telegram").getOrSetAs(TelegramConfig.class, TelegramConfig::new);
        openAI = main.subNode("openai").getOrSetAs(OpenAIConfig.class, OpenAIConfig::new);
        commonConfig = main.subNode("common").getOrSetAs(CommonConfig.class, CommonConfig::new);
        loggingConfig = main.subNode("logging").getOrSetAs(LoggingConfig.class, LoggingConfig::new);
    }
}
