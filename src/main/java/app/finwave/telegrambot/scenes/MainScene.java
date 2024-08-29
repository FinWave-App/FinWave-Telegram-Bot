package app.finwave.telegrambot.scenes;

import app.finwave.api.*;
import app.finwave.api.tools.ApiException;
import app.finwave.api.tools.IRequest;
import app.finwave.api.tools.Transaction;
import app.finwave.api.websocket.FinWaveWebSocketClient;
import app.finwave.api.websocket.messages.requests.NewNotificationPointRequest;
import app.finwave.api.websocket.messages.requests.SubscribeNotificationsRequest;
import app.finwave.tat.BotCore;
import app.finwave.tat.event.chat.NewMessageEvent;
import app.finwave.tat.handlers.AbstractChatHandler;
import app.finwave.tat.menu.BaseMenu;
import app.finwave.tat.scene.BaseScene;
import app.finwave.tat.utils.ComposedMessage;
import app.finwave.tat.utils.MessageBuilder;
import app.finwave.telegrambot.Main;
import app.finwave.telegrambot.config.CommonConfig;
import app.finwave.telegrambot.database.ChatDatabase;
import app.finwave.telegrambot.database.ChatPreferenceDatabase;
import app.finwave.telegrambot.database.DatabaseWorker;
import app.finwave.telegrambot.handlers.ChatHandler;
import app.finwave.telegrambot.jooq.tables.records.ChatsPreferencesRecord;
import app.finwave.telegrambot.jooq.tables.records.ChatsRecord;
import app.finwave.telegrambot.utils.*;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Document;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.model.WebAppInfo;
import com.pengrad.telegrambot.model.request.ChatAction;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.request.GetFile;
import com.pengrad.telegrambot.request.SendChatAction;
import com.pengrad.telegrambot.response.GetFileResponse;

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.concurrent.*;

public class MainScene extends BaseScene<Object> {
    protected BaseMenu menu;

    protected FinWaveClient client;
    protected FinWaveWebSocketClient webSocketClient;
    protected boolean websocketAuthed;

    protected ClientState state;

    protected ChatDatabase database;
    protected ChatPreferenceDatabase preferenceDatabase;
    protected ChatsPreferencesRecord preferencesRecord;

    protected CommonConfig commonConfig;

    protected List<Transaction> lastTransactions = new ArrayList<>();
    protected List<NoteApi.NoteEntry> notes = new ArrayList<>();

    protected long lastFetch = 0;
    protected Chat.Type chatType;

    protected ActionParser parser;

    protected AiWorker worker;

    protected boolean ignoreUpdates = false;

    protected static ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    public MainScene(AbstractChatHandler abstractChatHandler, DatabaseWorker databaseWorker, CommonConfig commonConfig) {
        super(abstractChatHandler);

        this.database = databaseWorker.get(ChatDatabase.class);
        this.commonConfig = commonConfig;
        this.preferenceDatabase = databaseWorker.get(ChatPreferenceDatabase.class);

        eventHandler.registerListener(NewMessageEvent.class, this::newMessage);
    }

    @Override
    public void start() {
        super.start();

        ChatsRecord record = database.getChat(this.chatId).orElseThrow();

        if (record.getType() == -1) {
            ((ChatHandler)abstractChatHandler).stopActiveScene();
            ((ChatHandler)abstractChatHandler).startScene("init");

            return;
        }

        this.chatType = Chat.Type.values()[record.getType()];
        this.preferencesRecord = preferenceDatabase.get(chatId);

        if (worker != null) {
            worker.setPreferredAccountId(preferencesRecord.getId());
        }

        if (client == null) {
            this.client = new FinWaveClient(record.getApiUrl(), record.getApiSession(), 5000, 5000);
            this.state = new ClientState(client);
            this.parser = new ActionParser(state);

            boolean aiEnabled = state.getConfigs().map((c) -> c.ai().enabled()).orElse(false);

            if (aiEnabled)
                worker = new AiWorker(client, preferencesRecord.getPreferredAccountId());
        }

        this.menu = new BaseMenu(this);

        if (webSocketClient == null || !webSocketClient.isOpen() || !websocketAuthed) {
            WebSocketHandler webSocketHandler = new WebSocketHandler(this, preferenceDatabase);

            try {
                this.webSocketClient = client.connectToWebsocket(webSocketHandler);
            } catch (URISyntaxException | InterruptedException e) {
                e.printStackTrace();
            }

            try {
                websocketAuthed = webSocketHandler.getAuthStatus().get(5, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                e.printStackTrace();
            }

            if (webSocketClient.isOpen() && websocketAuthed) {
                Optional<UUID> uuid = Optional.ofNullable(preferencesRecord.getNotificationUuid());

                if (uuid.isEmpty())
                    webSocketClient.send(new NewNotificationPointRequest("Telegram Bot", false));
                else
                    webSocketClient.send(new SubscribeNotificationsRequest(uuid.get()));
            }
        }

        try {
            if (!webSocketClient.isOpen() || !websocketAuthed || System.currentTimeMillis() - lastFetch > 30 * 60 * 1000)
                updateState();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ApiException apiException) {
                menu.setMessage(MessageBuilder.text("Упс, произошла ошибка: \"" + apiException.message.message() + "\""));

                menu.addButton(new InlineKeyboardButton("Перезапустить бота"), (event) -> {
                    this.client = null;
                    this.worker = null;

                    ((ChatHandler)abstractChatHandler).stopActiveScene();
                    ((ChatHandler)abstractChatHandler).startScene("init");
                });

                menu.apply();

                return;
            }else {
                throw new RuntimeException(e);
            }
        }

        update();
    }

    protected CompletableFuture<Boolean> appendTelegramFile(String fileId, String mime, String name) {
        return this.getChatHandler().getCore().execute(new GetFile(fileId))
                .thenApply((r) -> r.file().filePath())
                .thenApply((r) -> "https://api.telegram.org/file/bot" + Main.getBotToken() + "/" + r)
                .thenApply((r) -> {
                    try {
                        return worker.appendFile(r, mime, name).get();
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    protected void gptAnswer(String message) {
        ignoreUpdates = true;

        BotCore core = abstractChatHandler.getCore();
        SendChatAction typingStatus = new SendChatAction(chatId, ChatAction.typing);

        ScheduledFuture<?> future = executor.scheduleAtFixedRate(() -> core.execute(typingStatus),
                0, 2, TimeUnit.SECONDS);

        String answer;

        try {
            answer = worker.ask(message).get();
        }catch (Exception e) {
            e.printStackTrace();

            answer = "Произошла ошибка, повторите запрос позже";
        }finally {
            future.cancel(true);
        }

        menu.removeAllButtons();
        menu.setMaxButtonsInRow(1);

        menu.setMessage(MessageBuilder.text(answer));
        menu.addButton(new InlineKeyboardButton(EmojiList.ACCEPT + " Хорошо"), (e) -> {
            worker.initContext();
            ignoreUpdates = false;

            if (!webSocketClient.isOpen() || !websocketAuthed) {
                try {
                    updateState();
                } catch (ExecutionException | InterruptedException ignore) {}
            }

            update();
        });

        menu.apply();
    }

    protected void newMessage(NewMessageEvent event) {
        Optional<String> optionalText = Optional.ofNullable(event.data.text());

        if (chatType != Chat.Type.Private) {
            String myUsername = ((ChatHandler) getChatHandler()).getMe().username();

            boolean replyToMe = event.data.replyToMessage() != null && event.data.replyToMessage().from().username().equals(myUsername);

            String myUsernameWithPing = "@" + myUsername + " ";

            if ((optionalText.isEmpty() || !optionalText.get().contains(myUsernameWithPing)) && !replyToMe)
                return;

            optionalText = optionalText.map(
                    (text) -> text.replace(myUsernameWithPing, "")
            );

            abstractChatHandler.deleteMessage(abstractChatHandler.getLastSentMessageId());
            menu = new BaseMenu(this, false);
        }

        abstractChatHandler.deleteMessage(event.data.messageId());
        menu.removeAllButtons();
        menu.setMaxButtonsInRow(1);

        if (optionalText.isEmpty() && worker != null) {
            String caption = event.data.caption();
            CompletableFuture<Boolean> future = null;

            Document document = event.data.document();
            PhotoSize[] photoSizes = event.data.photo();

            if (photoSizes != null && photoSizes.length > 0) {
                future = appendTelegramFile(photoSizes[photoSizes.length - 1].fileId(), "image/jpeg", "Telegram Photo");
            }else if (document != null) {
                future = appendTelegramFile(document.fileId(), document.mimeType(), document.fileName());
            }

            if (future == null)
                return;

            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();

                menu.setMessage(MessageBuilder.text("Ошибка: недопустимый формат или серверный сбой"));
                menu.apply();

                return;
            }

            if (caption != null && !caption.isBlank()) {
                gptAnswer(caption);

                return;
            }

            menu.setMessage(MessageBuilder.text("Файл прикреплен к контексту " + EmojiList.ACCEPT));

            menu.addButton(new InlineKeyboardButton(EmojiList.CANCEL + " Отменить"), (e) -> {
                worker.initContext();
                ignoreUpdates = false;

                if (!webSocketClient.isOpen() || !websocketAuthed) {
                    try {
                        updateState();
                    } catch (ExecutionException | InterruptedException ignore) {}
                }

                update();
            });

            menu.apply();

            return;
        }

        String finalText = optionalText.get();

        GPTMode gptMode = GPTMode.of(preferencesRecord.getGptMode());

        if (gptMode == GPTMode.ALWAYS && worker != null) {
            gptAnswer(finalText);

            return;
        }

        IRequest<?> newRequest = parser.parse(finalText, preferencesRecord.getPreferredAccountId());

        if (newRequest == null && (gptMode == GPTMode.DISABLED || worker == null)) {
            menu.setMessage(MessageBuilder.text("Не удалось понять запрос. Попробуйте еще раз."));
            menu.addButton(new InlineKeyboardButton(EmojiList.BACK + " Назад"), (e) -> {
                if (worker != null)
                    worker.initContext();

                update();
            });

            menu.apply();
            return;
        }else if (newRequest == null) {
            gptAnswer(finalText);

            return;
        }

        if (!preferencesRecord.getAutoAcceptTransactions() && newRequest instanceof TransactionApi.NewTransactionRequest) {
            menu.setMessage(buildNewRequestView((TransactionApi.NewTransactionRequest) newRequest));
            menu.addButton(new InlineKeyboardButton("Подтвердить " + EmojiList.ACCEPT), (e) -> {
                client.runRequest(newRequest).whenComplete((r, t) -> {
                    if (worker != null)
                        worker.initContext();

                    if (!webSocketClient.isOpen() || !websocketAuthed) {
                        try {
                            updateState();
                        } catch (ExecutionException | InterruptedException ignore) {}
                    }

                    update();
                });
            });
            menu.addButton(new InlineKeyboardButton("Отмена " + EmojiList.CANCEL), (e) -> {
                if (worker != null)
                    worker.initContext();

                update();
            });

            if (worker != null)
                menu.addButton(new InlineKeyboardButton("Помощь ChatGPT " + EmojiList.BRAIN), (e) -> {
                    gptAnswer(finalText);
                });

            menu.apply();

            return;
        }

        client.runRequest(newRequest).whenComplete((r, t) -> {
            if (worker != null)
                worker.initContext();

            update();
        });
    }

    public synchronized void updateState() throws ExecutionException, InterruptedException {
        CompletableFuture.allOf(
                state.update(),
                state.fetchLastTransactions(10).whenComplete((r, t) -> {
                    if (t != null)
                        return;

                    lastTransactions = r;
                }),
                state.fetchImportantNotes().whenComplete((r, t) -> {
                    if (t != null)
                        return;

                    notes = r;
                })
        ).get();

        lastFetch = System.currentTimeMillis();
    }

    public synchronized void update() {
        if (ignoreUpdates)
            return;

        menu.removeAllButtons();
        menu.setMaxButtonsInRow(1);

        MessageBuilder builder = MessageBuilder.create();

        builder.append(buildAccountsView().text());

        builder.append(buildTransactionsView().text()).gap();

        if (!notes.isEmpty()) {
            builder.append(buildNotesView().text()).gap();
        }

        if (preferencesRecord.getTipsShowed()) {
            builder.append(buildTipsView().text());
        }

        if (!webSocketClient.isOpen() || !websocketAuthed)
            builder.gap().line(EmojiList.WARNING + " Ошибка подключения к серверу через веб-сокет. Автоматическое обновление и уведомления недоступны");

        menu.addButton(new InlineKeyboardButton("Настройки " + EmojiList.SETTINGS), (e) -> {
            ChatHandler handler = (ChatHandler) abstractChatHandler;

            handler.stopActiveScene();
            handler.startScene("settings", state);
        });

        try {
            String webappUrl = new URL(commonConfig.defaultApiUrl).getHost();
            webappUrl = "https://" + webappUrl + "/?autologin=" + client.getToken();

            if (chatType == Chat.Type.Private) {
                menu.addButton(new InlineKeyboardButton("Веб-приложение").webApp(new WebAppInfo(webappUrl)));
            }else {
                builder.gap().url(webappUrl, "Веб-приложение");
            }

        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        menu.setMessage(builder.build());

        try {
            menu.apply().get();
        } catch (InterruptedException | ExecutionException ignored) {
            menu.setSentMessage(-1);

            try {
                menu.apply().get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    protected ComposedMessage buildNewRequestView(TransactionApi.NewTransactionRequest newRequest) {
        MessageBuilder builder = MessageBuilder.create("Подтвердите новую транзакцию: ").gap();

        AccountApi.AccountEntry account = state.getAccountsMap().get(newRequest.accountId());
        AccountFolderApi.FolderEntry folder = state.getAccountFoldersMap().get(account.folderId());

        builder.line(EmojiList.ACCOUNT + " Счет: " + account.name() + " (" + folder.name() + ")");
        builder.line(EmojiList.TAG + " Тег: " + state.getTransactionCategoriesMap().get(newRequest.categoryId()).name());
        builder.line(EmojiList.WARNING + " Сумма: " + state.formatAmount(newRequest.delta(), newRequest.accountId(), true, preferencesRecord.getHideAmounts()));

        if (newRequest.description() != null)
            builder.line(EmojiList.CLIPBOARD + " Описание: " + newRequest.description());

        return builder.build();
    }


    protected ComposedMessage buildTipsView() {
        MessageBuilder builder = MessageBuilder.create("Советы:").gap();

        if (preferencesRecord.getPreferredAccountId() == -1) {
            builder.line(EmojiList.WARNING + " Выберите предпочитаемый счет в настройках.");
        }

        builder.gap()
                .line(EmojiList.LIGHT_BULB + " Для добавления новой транзакции вам необходимо указать сумму (по умолчанию это будет расход, если не поставить знак '+'), а также название тега и счета.").gap()
                .line(EmojiList.ACCOUNT + " Если указан предпочитаемый счет, то его можно не указывать").gap()
                .line(EmojiList.BOT + " Бот постарается определить нужный тег на основе введенных букв и знака суммы.").gap()
                .line(EmojiList.BRAIN + " Если все данные распознаны верно, остальные слова будут добавлены в описание транзакции.").gap()
                .line(EmojiList.SPEECH_BALLOON + " Для добавления заметки введите символ '!' перед текстом.").gap();

        return builder.build();
    }

    protected ComposedMessage buildNotesView() {
        MessageBuilder builder = MessageBuilder.create(EmojiList.SPEECH_BALLOON + " Заметки:");
        DateTimeFormatter formatter = DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(Locale.forLanguageTag("ru"))
                .withZone(ZoneId.of("UTC+3"));

        for (NoteApi.NoteEntry note : notes) {
            builder.gap();

            if (note.notificationTime() != null)
                builder.bold()
                        .append(formatter.format(note.notificationTime()))
                        .bold()
                        .append(" ");

            builder.line(note.text());
        }

        return builder.build();
    }

    protected ComposedMessage buildTransactionsView() {
        MessageBuilder builder = MessageBuilder.create(EmojiList.ACCOUNT + " Последние транзакции:").gap();

        var accountsMap = state.getAccountsMap();
        var tagsMap = state.getTransactionCategoriesMap();

        for (int i = 0; i < lastTransactions.size(); i++) {
            Transaction transaction = lastTransactions.get(i);

            AccountApi.AccountEntry account = accountsMap.get(transaction.accountId());
            TransactionCategoryApi.CategoryEntry category = tagsMap.get(transaction.categoryId());

            BigDecimal delta = transaction.delta();

            String treeDecorate = i == lastTransactions.size() - 1 ? "└  " : "├  ";
            builder.append(treeDecorate);

            builder.append(state.formatAmount(delta, account.accountId(), true, preferencesRecord.getHideAmounts()))
                    .append(": ")
                    .append(account.name())
                    .append(", ")
                    .append(category.name());

            if (transaction.description() != null) {
                builder.append(", " + transaction.description());
            }

            builder.gap();
        }

        return builder.build();
    }

    protected ComposedMessage buildAccountsView() {
        MessageBuilder builder = MessageBuilder.create();
        var toRender = state.getAccountsByTags();

        for (var entry : toRender.entrySet()) {
            List<AccountApi.AccountEntry> accounts = entry.getValue().stream().filter(a -> !a.hidden()).toList();
            builder.line(entry.getKey().name());

            for (int i = 0; i < accounts.size(); i++) {
                AccountApi.AccountEntry account = accounts.get(i);

                String treeDecorate = i == accounts.size() - 1 ? "└─" : "├─";
                builder.append(treeDecorate);

                if (account.accountId() == preferencesRecord.getPreferredAccountId())
                    builder.bold().append(account.name()).bold();
                else
                    builder.append(account.name());

                builder.append(": " + state.formatAmount(account.amount(), account.accountId(), false, preferencesRecord.getHideAmounts()));
                builder.gap();
            }

            builder.gap();
        }

        return builder.build();
    }
}
