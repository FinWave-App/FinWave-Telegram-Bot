package app.finwave.telegrambot.logging;

import app.finwave.telegrambot.Main;
import app.finwave.telegrambot.config.ConfigWorker;
import app.finwave.telegrambot.config.LoggingConfig;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

public class LoggerFactory implements ILoggerFactory {
    protected static LoggingConfig config;

    static {
        config = Main.getINJ().getInstance(ConfigWorker.class).loggingConfig;
    }

    @Override
    public Logger getLogger(String name) {
        if (!config.logFullClassName) {
            String[] split = name.split("\\.");
            name = split[split.length - 1];
        }

        return new app.finwave.telegrambot.logging.Logger(name);
    }
}
