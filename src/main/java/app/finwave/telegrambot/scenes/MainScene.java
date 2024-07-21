package app.finwave.telegrambot.scenes;

import app.finwave.api.*;
import app.finwave.api.tools.ApiException;
import app.finwave.api.tools.Transaction;
import app.finwave.api.websocket.FinWaveWebSocketClient;
import app.finwave.api.websocket.messages.requests.NewNotificationPointRequest;
import app.finwave.api.websocket.messages.requests.SubscribeNotificationsRequest;
import app.finwave.tat.event.chat.NewMessageEvent;
import app.finwave.tat.handlers.AbstractChatHandler;
import app.finwave.tat.menu.BaseMenu;
import app.finwave.tat.scene.BaseScene;
import app.finwave.tat.utils.ComposedMessage;
import app.finwave.tat.utils.MessageBuilder;
import app.finwave.tat.utils.Stack;
import app.finwave.telegrambot.config.CommonConfig;
import app.finwave.telegrambot.config.OpenAIConfig;
import app.finwave.telegrambot.database.ChatDatabase;
import app.finwave.telegrambot.database.ChatPreferenceDatabase;
import app.finwave.telegrambot.database.DatabaseWorker;
import app.finwave.telegrambot.handlers.ChatHandler;
import app.finwave.telegrambot.jooq.tables.records.ChatsPreferencesRecord;
import app.finwave.telegrambot.jooq.tables.records.ChatsRecord;
import app.finwave.telegrambot.utils.*;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.WebAppInfo;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.request.SendChatAction;

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class MainScene extends BaseScene<Object> {
    protected BaseMenu menu;

    protected FinWaveClient client;
    protected FinWaveWebSocketClient webSocketClient;

    protected ClientState state;

    protected ChatDatabase database;
    protected ChatPreferenceDatabase preferenceDatabase;
    protected ChatsPreferencesRecord preferencesRecord;

    protected CommonConfig commonConfig;

    protected List<Transaction> lastTransactions;
    protected ActionParser parser;

    protected OpenAIWorker aiWorker;

    protected GPTContext gptContext = new GPTContext(20);

    public MainScene(AbstractChatHandler abstractChatHandler, DatabaseWorker databaseWorker, CommonConfig commonConfig, OpenAIWorker aiWorker) {
        super(abstractChatHandler);

        this.database = databaseWorker.get(ChatDatabase.class);
        this.commonConfig = commonConfig;
        this.preferenceDatabase = databaseWorker.get(ChatPreferenceDatabase.class);
        this.aiWorker = aiWorker;

        eventHandler.registerListener(NewMessageEvent.class, this::newMessage);
    }

    @Override
    public void start() {
        super.start();

        ChatsRecord record = database.getChat(this.chatId).orElseThrow();

        this.client = new FinWaveClient(record.getApiUrl(), record.getApiSession(), 5000, 5000);
        this.state = new ClientState(client);
        this.preferencesRecord = preferenceDatabase.get(chatId);

        this.menu = new BaseMenu(this);

        this.parser = new ActionParser(state);

        try {
            if (webSocketClient != null && webSocketClient.isOpen())
                webSocketClient.close();

            this.webSocketClient = client.connectToWebsocket(new WebSocketHandler(this, preferenceDatabase));
        } catch (URISyntaxException | InterruptedException e) {
            e.printStackTrace();
        }

        Optional<UUID> uuid = Optional.ofNullable(preferencesRecord.getNotificationUuid());

        if (uuid.isEmpty())
            webSocketClient.send(new NewNotificationPointRequest("Telegram Bot", false));
        else
            webSocketClient.send(new SubscribeNotificationsRequest(uuid.get()));

        update();
    }

    protected void gptAnswer() {
        String answer = aiWorker.answer(gptContext, state, preferencesRecord, 5);

        menu.removeAllButtons();

        menu.setMessage(MessageBuilder.text(answer));
        menu.addButton(new InlineKeyboardButton(EmojiList.ACCEPT + " Хорошо"), (e) -> {
            gptContext.clear();

            update();
        });

        menu.apply();
    }

    protected void newMessage(NewMessageEvent event) {
        GPTMode gptMode = GPTMode.of(preferencesRecord.getGptMode());
        gptContext.push(event.data);

        abstractChatHandler.deleteMessage(event.data.messageId());
        menu.removeAllButtons();
        menu.setMaxButtonsInRow(1);

        String text = event.data.text();

        if (gptMode == GPTMode.ALWAYS) {
            gptAnswer();

            return;
        }

        TransactionApi.NewTransactionRequest newRequest = parser.parse(text, preferencesRecord.getPreferredAccountId());

        if (newRequest == null && gptMode == GPTMode.DISABLED) {
            menu.setMessage(MessageBuilder.text("Не удалось понять запрос. Попробуйте еще раз."));
            menu.addButton(new InlineKeyboardButton(EmojiList.BACK + " Назад"), (e) -> {
                gptContext.clear();

                update();
            });

            menu.apply();
            return;
        }else if (newRequest == null) {
            gptAnswer();

            return;
        }

        if (!preferencesRecord.getAutoAcceptTransactions()) {
            menu.setMessage(buildNewRequestView(newRequest));
            menu.addButton(new InlineKeyboardButton("Подтвердить " + EmojiList.ACCEPT), (e) -> {
                client.runRequest(newRequest).whenComplete((r, t) -> {
                    gptContext.clear();

                    update();
                });
            });
            menu.addButton(new InlineKeyboardButton("Отмена " + EmojiList.CANCEL), (e) -> {
                gptContext.clear();

                update();
            });
            menu.addButton(new InlineKeyboardButton("Помощь ChatGPT " + EmojiList.BRAIN), (e) -> {
                gptAnswer();
            });

            menu.apply();

            return;
        }

        client.runRequest(newRequest).whenComplete((r, t) -> {
            gptContext.clear();

            update();
        });
    }

    public void update() {
        menu.removeAllButtons();
        menu.setMaxButtonsInRow(1);

        try {
            CompletableFuture.allOf(
                    state.update(),
                    state.fetchLastTransactions(10).whenComplete((r, t) -> {
                        if (t != null)
                            return;

                        lastTransactions = r;
                    })
            ).get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ApiException apiException) {
                menu.setMessage(MessageBuilder.text("Упс, произошла ошибка: \"" + apiException.message.message() + "\""));

                menu.addButton(new InlineKeyboardButton("Перезапустить бота"), (event) -> {
                    ((ChatHandler)abstractChatHandler).stopActiveScene();
                    ((ChatHandler)abstractChatHandler).startScene("init");
                });

                menu.apply();

                return;
            }else {
                throw new RuntimeException(e);
            }
        }

        MessageBuilder builder = MessageBuilder.create();

        builder.append(buildAccountsView().text());

        builder.line("Последние транзакции:");

        builder.append(buildTransactionsView().text()).gap();

        if (preferencesRecord.getTipsShowed()) {
            builder.append(buildTipsView().text());
        }

        menu.setMessage(builder.build());
        menu.addButton(new InlineKeyboardButton("Настройки " + EmojiList.SETTINGS), (e) -> {
            ChatHandler handler = (ChatHandler) abstractChatHandler;

            handler.stopActiveScene();
            handler.startScene("settings", state);
        });

        try {
            String webappUrl = new URL(commonConfig.defaultApiUrl).getHost();
            webappUrl = "https://" + webappUrl + "/?autologin=" + client.getToken();

            menu.addButton(new InlineKeyboardButton("Веб-приложение").webApp(new WebAppInfo(webappUrl)));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        menu.apply();
    }

    protected ComposedMessage buildNewRequestView(TransactionApi.NewTransactionRequest newRequest) {
        MessageBuilder builder = MessageBuilder.create("Подтвердите новую транзакцию: ").gap();

        builder.line(EmojiList.ACCOUNT + " Счет: " + state.getAccountsMap().get(newRequest.accountId()).name());
        builder.line(EmojiList.TAG + " Тег: " + state.getTransactionTagsMap().get(newRequest.tagId()).name());
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
                .line("Для добавления новой транзакции вам необходимо указать сумму (по умолчанию это будет расход, если не поставить знак +), а также название тега и счета.")
                .line("Если указан предпочитаемый счет, то его можно не указывать. Бот постарается определить нужный тег на основе введенных букв и знака суммы.")
                .line("Если все данные распознаны верно, остальные слова будут добавлены в описание транзакции.");

        return builder.build();
    }

    protected ComposedMessage buildTransactionsView() {
        MessageBuilder builder = MessageBuilder.create();

        var accountsMap = state.getAccountsMap();
        var tagsMap = state.getTransactionTagsMap();

        for (Transaction transaction : lastTransactions) {
            AccountApi.AccountEntry account = accountsMap.get(transaction.accountId());
            TransactionTagApi.TagEntry tag = tagsMap.get(transaction.tagId());

            BigDecimal delta = transaction.delta();

            builder.append(state.formatAmount(delta, account.accountId(), true, preferencesRecord.getHideAmounts()))
                    .append(": ")
                    .append(account.name())
                    .append(", ")
                    .append(tag.name());

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
