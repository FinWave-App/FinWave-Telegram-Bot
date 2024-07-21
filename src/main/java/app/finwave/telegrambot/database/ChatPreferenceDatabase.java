package app.finwave.telegrambot.database;

import app.finwave.telegrambot.jooq.tables.records.ChatsPreferencesRecord;
import app.finwave.telegrambot.utils.GPTMode;
import org.jooq.DSLContext;

import java.util.Optional;
import java.util.UUID;

import static app.finwave.telegrambot.jooq.Tables.CHATS_PREFERENCES;
import static app.finwave.telegrambot.jooq.tables.Chats.CHATS;

public class ChatPreferenceDatabase extends AbstractDatabase {
    public ChatPreferenceDatabase(DSLContext context) {
        super(context);
    }

    public void create(long chatId) {
        context.insertInto(CHATS_PREFERENCES)
                .set(CHATS_PREFERENCES.CHAT_ID, chatId)
                .onConflict(CHATS_PREFERENCES.CHAT_ID)
                .doUpdate()
                .set(CHATS_PREFERENCES.PREFERRED_ACCOUNT_ID, -1L)
                .set(CHATS_PREFERENCES.GPT_MODE, 0)
                .set(CHATS_PREFERENCES.TIPS_SHOWED, true)
                .set(CHATS_PREFERENCES.AUTO_ACCEPT_TRANSACTIONS, false)
                .set(CHATS_PREFERENCES.HIDE_AMOUNTS, false)
                .set(CHATS_PREFERENCES.NOTIFICATION_UUID, (UUID) null)
                .where(CHATS_PREFERENCES.CHAT_ID.eq(chatId))
                .execute();
    }

    public ChatsPreferencesRecord get(long chatId) {
        return context.selectFrom(CHATS_PREFERENCES)
                .where(CHATS_PREFERENCES.CHAT_ID.eq(chatId))
                .fetchOptional().orElse(null);
    }

    public void setPreferredAccountId(long chatId, long accountId) {
        context.update(CHATS_PREFERENCES)
                .set(CHATS_PREFERENCES.PREFERRED_ACCOUNT_ID, accountId)
                .where(CHATS_PREFERENCES.CHAT_ID.eq(chatId))
                .execute();
    }

    public void setGPTMode(long chatId, GPTMode mode) {
        context.update(CHATS_PREFERENCES)
                .set(CHATS_PREFERENCES.GPT_MODE, mode.mode)
                .where(CHATS_PREFERENCES.CHAT_ID.eq(chatId))
                .execute();
    }

    public void setTipsShowed(long chatId, boolean showed) {
        context.update(CHATS_PREFERENCES)
                .set(CHATS_PREFERENCES.TIPS_SHOWED, showed)
                .where(CHATS_PREFERENCES.CHAT_ID.eq(chatId))
                .execute();
    }

    public void setAutoAcceptTransactions(long chatId, boolean accept) {
        context.update(CHATS_PREFERENCES)
                .set(CHATS_PREFERENCES.AUTO_ACCEPT_TRANSACTIONS, accept)
                .where(CHATS_PREFERENCES.CHAT_ID.eq(chatId))
                .execute();
    }

    public void setHideAmounts(long chatId, boolean hide) {
        context.update(CHATS_PREFERENCES)
                .set(CHATS_PREFERENCES.HIDE_AMOUNTS, hide)
                .where(CHATS_PREFERENCES.CHAT_ID.eq(chatId))
                .execute();
    }

    public void setNotificationUUID(long chatId, UUID uuid) {
        context.update(CHATS_PREFERENCES)
                .set(CHATS_PREFERENCES.NOTIFICATION_UUID, uuid)
                .where(CHATS_PREFERENCES.CHAT_ID.eq(chatId))
                .execute();
    }
}
