package app.finwave.telegrambot;

import app.finwave.api.AuthApi;
import app.finwave.api.FinWaveClient;
import app.finwave.tat.BotCore;
import app.finwave.telegrambot.config.ConfigWorker;
import app.finwave.telegrambot.database.ChatDatabase;
import app.finwave.telegrambot.database.DatabaseWorker;
import app.finwave.telegrambot.handlers.ChatHandler;
import app.finwave.telegrambot.handlers.GlobalHandler;
import app.finwave.telegrambot.handlers.UserHandler;
import app.finwave.telegrambot.logging.LogsInitializer;
import app.finwave.telegrambot.utils.OpenAIWorker;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class Main {
    protected static Injector INJ;
    protected static Logger log;

    public static void main(String[] args) throws IOException {
        ConfigWorker configWorker = new ConfigWorker();
        BotCore core = new BotCore(configWorker.telegram.apiToken);

        INJ = Guice.createInjector(binder -> {
            binder.bind(BotCore.class).toInstance(core);
            binder.bind(ConfigWorker.class).toInstance(configWorker);
        });

        LogsInitializer.init();
        log = LoggerFactory.getLogger(Main.class);

        DatabaseWorker databaseWorker = INJ.getInstance(DatabaseWorker.class);
        OpenAIWorker aiWorker = INJ.getInstance(OpenAIWorker.class);

        core.setHandlers(new GlobalHandler(core),
                chatId -> new ChatHandler(core, databaseWorker, configWorker.commonConfig, aiWorker, chatId),
                userId -> new UserHandler(core, userId)
        );

        log.info("Bot started");
    }

    public static Injector getINJ() {
        return INJ;
    }
}