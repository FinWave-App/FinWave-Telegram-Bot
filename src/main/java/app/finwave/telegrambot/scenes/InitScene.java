package app.finwave.telegrambot.scenes;

import app.finwave.api.ConfigApi;
import app.finwave.api.FinWaveClient;
import app.finwave.api.UserApi;
import app.finwave.tat.event.chat.NewMessageEvent;
import app.finwave.tat.event.handler.HandlerRemover;
import app.finwave.tat.handlers.AbstractChatHandler;
import app.finwave.tat.menu.BaseMenu;
import app.finwave.tat.scene.BaseScene;
import app.finwave.tat.utils.ComposedMessage;
import app.finwave.tat.utils.MessageBuilder;
import app.finwave.telegrambot.config.CommonConfig;
import app.finwave.telegrambot.database.ChatDatabase;
import app.finwave.telegrambot.database.ChatPreferenceDatabase;
import app.finwave.telegrambot.database.DatabaseWorker;
import app.finwave.telegrambot.handlers.ChatHandler;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ExecutionException;

public class InitScene extends BaseScene<Object> {
    protected CommonConfig commonConfig;
    protected ChatDatabase chatDatabase;
    protected ChatPreferenceDatabase chatPreferences;

    protected BaseMenu menu;
    protected URL serverUrl;

    protected HandlerRemover newMessageRemover;

    public InitScene(AbstractChatHandler abstractChatHandler, DatabaseWorker databaseWorker, CommonConfig commonConfig) {
        super(abstractChatHandler);

        this.commonConfig = commonConfig;
        this.chatDatabase = databaseWorker.get(ChatDatabase.class);
        this.chatPreferences = databaseWorker.get(ChatPreferenceDatabase.class);
    }

    @Override
    public void start() {
        super.start();

        menu = new BaseMenu(this);

        ComposedMessage helloMessage = MessageBuilder.create()
                .line("Привет! Я — Telegram-бот для быстрой записи транзакций в FinWave и не только. Давайте начнем...")
                .gap()
                .line("Для начала мне нужно понять, с чем работать.")
                .build();

        menu.setMessage(helloMessage);

        if (!commonConfig.allowCustomUrl) {
            try {
                serverUrl = new URL(commonConfig.defaultApiUrl);
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }

            askSession();

            return;
        }

        askServer();
    }

    public void askServer() {
        MessageBuilder builder = MessageBuilder.create(menu.getMessage())
                .gap()
                .line("Нажмите на кнопку ниже, чтобы выбрать стандартный сервер или напишите в чат свой")
                .line("Вы можете указать как просто домен (demo.finwave.app), так и целиком URL до API (https://demo.finwave.app/api)");

        menu.setMessage(builder.build());
        menu.addButton(new InlineKeyboardButton(commonConfig.defaultUrlName), (e) -> {
            try {
                serverUrl = new URL(commonConfig.defaultApiUrl);
                menu.setMessage(null);
                askSession();
            } catch (MalformedURLException ex) {
                throw new RuntimeException(ex);
            }
        });

        newMessageRemover = eventHandler.registerListener(NewMessageEvent.class, (e) -> {
            if (e.data.text() == null)
                return;

            abstractChatHandler.deleteMessage(e.data.messageId());
            String rawURL = e.data.text();

            try {
                if (!rawURL.startsWith("http"))
                    rawURL = "https://" + rawURL + "/api/";

                if (!rawURL.endsWith("/"))
                    rawURL = rawURL + "/";

                serverUrl = new URL(rawURL);
            }catch (Exception ignored) {

            }

            if (!testServer()) {
                menu.setMessage(MessageBuilder.text("Кажется, URL невалидный. Попробуйте еще раз"));
                menu.apply();

                return;
            }

            menu.setMessage(null);
            askSession();
        });

        menu.apply();
    }

    public boolean testServer() {
        if (serverUrl == null)
            return false;

        FinWaveClient finWaveClient = new FinWaveClient(serverUrl.toString());
        try {
            return finWaveClient.runRequest(new ConfigApi.GetConfigsRequest()).get() != null;
        } catch (InterruptedException | ExecutionException e) {
            return false;
        }
    }

    public void askSession() {
        if (newMessageRemover != null)
            newMessageRemover.remove();

        menu.removeAllButtons();

        MessageBuilder builder;
        String instructions = "Для этого перейдите на сайт, войдите в систему, нажмите на свой юзернейм и откройте вкладку \"Сеансы\". Создайте новый сеанс и отправьте его сюда.";

        if (menu.getMessage() == null) {
            builder = MessageBuilder.create()
                    .line("Отлично! Теперь привяжем ваш аккаунт.")
                    .gap()
                    .line(instructions);
        }else {
            builder = MessageBuilder.create(menu.getMessage())
                    .gap()
                    .line("Начнем с привязки аккаунта.")
                    .gap()
                    .line(instructions);
        }

        String serverHost = serverUrl.getHost();

        menu.setMessage(builder.build());
        menu.addButton(new InlineKeyboardButton("Перейти на " + serverHost, "https://" + serverHost));

        newMessageRemover = eventHandler.registerListener(NewMessageEvent.class, (e) -> {
            if (e.data.text() == null)
                return;

            abstractChatHandler.deleteMessage(e.data.messageId());

            String session = e.data.text();

            FinWaveClient client = new FinWaveClient(serverUrl.toString(), session);
            UserApi.UsernameResponse response = null;
            try {
                response = client.runRequest(new UserApi.UsernameRequest()).get();
            } catch (Exception ignored) {

            }

            if (response == null || response.username() == null || response.username().isBlank()) {
                menu.setMessage(MessageBuilder.text("Кажется, токен невалидный. Попробуйте еще раз"));
                menu.apply();

                return;
            }

            chatDatabase.registerChat(chatId, serverUrl.toString(), session, (short) e.data.chat().type().ordinal(), abstractChatHandler.getLastSentMessage().messageId());
            chatPreferences.create(chatId);

            ChatHandler handler = (ChatHandler) abstractChatHandler;
            handler.stopActiveScene();
            handler.startScene("main");
        });

        menu.apply();
    }
}
