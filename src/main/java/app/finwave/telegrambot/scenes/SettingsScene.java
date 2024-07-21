package app.finwave.telegrambot.scenes;

import app.finwave.tat.handlers.AbstractChatHandler;
import app.finwave.tat.menu.BaseMenu;
import app.finwave.tat.scene.BaseScene;
import app.finwave.tat.utils.MessageBuilder;
import app.finwave.telegrambot.database.ChatPreferenceDatabase;
import app.finwave.telegrambot.database.DatabaseWorker;
import app.finwave.telegrambot.handlers.ChatHandler;
import app.finwave.telegrambot.jooq.tables.records.ChatsPreferencesRecord;
import app.finwave.telegrambot.utils.ClientState;
import app.finwave.telegrambot.utils.EmojiList;
import app.finwave.telegrambot.utils.GPTMode;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.Optional;

public class SettingsScene extends BaseScene<ClientState> {
    protected ChatPreferenceDatabase database;
    protected ClientState state;
    protected BaseMenu menu;

    public SettingsScene(AbstractChatHandler abstractChatHandler, DatabaseWorker databaseWorker) {
        super(abstractChatHandler);

        this.database = databaseWorker.get(ChatPreferenceDatabase.class);
    }


    @Override
    public void start(ClientState state) {
        super.start();

        this.state = state;
        menu = new BaseMenu(this);

        showMain();
    }

    public void showMain() {
        menu.removeAllButtons();

        ChatsPreferencesRecord record = database.get(chatId);

        MessageBuilder builder = MessageBuilder.create();

        builder.append(EmojiList.ACCOUNT + " Предпочитаемый счет: ");

        if (record.getPreferredAccountId() == -1) {
            builder.append("отсутствует");
        }else {
            builder.append(state.getAccountsMap().get(record.getPreferredAccountId()).name());
        }
        builder.gap();

        builder.append(EmojiList.BRAIN + " Режим GPT: ").append(GPTMode.of(record.getGptMode()).name.toLowerCase()).gap();
        builder.append(EmojiList.LIGHT_BULB + " Подсказки: ").append(record.getTipsShowed() ? "включены" : "отключены").gap();
        builder.append(EmojiList.CLIPBOARD + " Авто-подтверждение транзакций: ").append(record.getAutoAcceptTransactions() ? "включены" : "отключены").gap();
        builder.append(EmojiList.EYES + " Скрытие сумм: ").append(record.getHideAmounts() ? "да" : "нет");

        menu.setMessage(builder.build());
        menu.setButtonsInRows(2, 1, 1, 1, 1);

        menu.addButton(new InlineKeyboardButton("Указать счет " + EmojiList.ACCOUNT), (e) -> selectAccount());
        menu.addButton(new InlineKeyboardButton("Изменить режим GPT " + EmojiList.BRAIN), (e) -> editGPTMode());
        menu.addButton(new InlineKeyboardButton("Переключить подсказки " + EmojiList.LIGHT_BULB), (e) -> {
           database.setTipsShowed(chatId, !record.getTipsShowed());

           showMain();
        });

        menu.addButton(new InlineKeyboardButton("Переключить авто-подтверждение " + EmojiList.CLIPBOARD), (e) -> {
            database.setAutoAcceptTransactions(chatId, !record.getAutoAcceptTransactions());

            showMain();
        });

        menu.addButton(new InlineKeyboardButton("Переключить скрытие сумм " + EmojiList.EYES), (e) -> {
            database.setHideAmounts(chatId, !record.getHideAmounts());

            showMain();
        });

        menu.addButton(new InlineKeyboardButton(EmojiList.BACK + " Назад"), (e) -> {
            ChatHandler handler = (ChatHandler) abstractChatHandler;

            handler.stopActiveScene();
            handler.startScene("main");
        });

        menu.apply();
    }

    public void editGPTMode() {
        menu.removeAllButtons();
        menu.setButtonsInRows((ArrayList<Integer>) null);
        menu.setMaxButtonsInRow(1);

        menu.setMessage(MessageBuilder.text("Укажите режим GPT"));

        for (GPTMode mode : GPTMode.values()) {
            menu.addButton(new InlineKeyboardButton(mode.name), (e) -> {
                database.setGPTMode(chatId, mode);

                showMain();
            });
        }

        menu.addButton(new InlineKeyboardButton("Отменить " + EmojiList.CANCEL), (e) -> showMain());

        menu.apply();
    }

    public void selectAccount() {
        menu.removeAllButtons();
        menu.setButtonsInRows((ArrayList<Integer>) null);
        menu.setMaxButtonsInRow(1);

        menu.setMessage(MessageBuilder.text("Укажите предпочитаемый счет"));

        for (var account : state.getAccounts()) {
            String showedDescription = "";

            if (account.description() != null && !account.description().isBlank()) {
                showedDescription = account.description();

                if (showedDescription.length() > 20)
                    showedDescription = showedDescription.substring(0, 17) + "...";

                showedDescription = " (" + showedDescription + ")";
            }

            menu.addButton(new InlineKeyboardButton(account.name() + showedDescription), (e) -> {
                database.setPreferredAccountId(chatId, account.accountId());

                showMain();
            });
        }

        menu.addButton(new InlineKeyboardButton("Отменить " + EmojiList.CANCEL), (e) -> showMain());

        menu.apply();
    }
}
