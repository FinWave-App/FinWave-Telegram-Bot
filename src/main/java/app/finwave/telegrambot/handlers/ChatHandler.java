package app.finwave.telegrambot.handlers;

import app.finwave.tat.BotCore;
import app.finwave.tat.handlers.scened.ScenedAbstractChatHandler;
import app.finwave.tat.scene.BaseScene;
import app.finwave.telegrambot.config.CommonConfig;
import app.finwave.telegrambot.config.OpenAIConfig;
import app.finwave.telegrambot.database.ChatDatabase;
import app.finwave.telegrambot.database.DatabaseWorker;
import app.finwave.telegrambot.jooq.tables.records.ChatsRecord;
import app.finwave.telegrambot.scenes.InitScene;
import app.finwave.telegrambot.scenes.MainScene;
import app.finwave.telegrambot.scenes.NotificationScene;
import app.finwave.telegrambot.scenes.SettingsScene;
import app.finwave.telegrambot.utils.OpenAIWorker;

import java.util.Optional;

public class ChatHandler extends ScenedAbstractChatHandler {
    protected ChatDatabase chatDatabase;
    protected NotificationScene notificationScene;

    public ChatHandler(BotCore core, DatabaseWorker databaseWorker, CommonConfig commonConfig, OpenAIWorker aiWorker, long chatId) {
        super(core, chatId);

        this.chatDatabase = databaseWorker.get(ChatDatabase.class);
        this.notificationScene = new NotificationScene(this);

        registerScene("init", new InitScene(this, databaseWorker, commonConfig));
        registerScene("main", new MainScene(this, databaseWorker, commonConfig, aiWorker));
        registerScene("settings", new SettingsScene(this, databaseWorker));
        registerScene("notification", notificationScene);
    }

    @Override
    public void start() {
        Optional<ChatsRecord> chat = chatDatabase.getChat(chatId);

        if (chat.isEmpty()) {
            startScene("init");
            return;
        }

        startScene("main", chat.get());
    }

    public NotificationScene getNotificationScene() {
        return notificationScene;
    }

    public BaseScene<?> getActiveScene() {
        return activeScenes.peek();
    }
}
