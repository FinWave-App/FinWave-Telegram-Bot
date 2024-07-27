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
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.GetMe;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class ChatHandler extends ScenedAbstractChatHandler {
    protected ChatDatabase chatDatabase;
    protected NotificationScene notificationScene;
    protected User me;

    public ChatHandler(BotCore core, DatabaseWorker databaseWorker, CommonConfig commonConfig, OpenAIConfig aiConfig, OpenAIWorker aiWorker, long chatId) {
        super(core, chatId);

        this.chatDatabase = databaseWorker.get(ChatDatabase.class);
        this.notificationScene = new NotificationScene(this);

        registerScene("init", new InitScene(this, databaseWorker, commonConfig));
        registerScene("main", new MainScene(this, databaseWorker, commonConfig, aiConfig, aiWorker));
        registerScene("settings", new SettingsScene(this, databaseWorker, aiConfig));
        registerScene("notification", notificationScene);

        sentMessages.setLastItemWatcher((m) -> {
            if (m != null && m.second() != null) // ignore database update if last chat id loaded from database
                chatDatabase.updateLastMessage(chatId, m.first());
        });
    }

    @Override
    public void start() {
        Optional<ChatsRecord> chat = chatDatabase.getChat(chatId);
        try {
            me = core.execute(new GetMe()).get().user();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        if (chat.isEmpty()) {
            startScene("init");
            return;
        }

        ChatsRecord record = chat.get();
        int lastMessage = record.getLastMessage();

        if (lastMessage != -1)
            pushLastSentMessageId(lastMessage);

        startScene("main", record);
    }

    public User getMe() {
        return me;
    }

    public NotificationScene getNotificationScene() {
        return notificationScene;
    }

    public BaseScene<?> getActiveScene() {
        return activeScenes.peek();
    }
}
