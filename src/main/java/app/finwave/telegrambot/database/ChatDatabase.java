package app.finwave.telegrambot.database;

import app.finwave.telegrambot.jooq.tables.Chats;
import app.finwave.telegrambot.jooq.tables.records.ChatsRecord;
import org.jooq.DSLContext;

import java.util.Optional;

import static app.finwave.telegrambot.jooq.tables.Chats.CHATS;

public class ChatDatabase extends AbstractDatabase {
    public ChatDatabase(DSLContext context) {
        super(context);
    }

    public void registerChat(long chatId, String apiUrl, String session) {
        context.insertInto(CHATS)
                .set(CHATS.ID, chatId)
                .set(CHATS.API_URL, apiUrl)
                .set(CHATS.API_SESSION, session)
                .onConflict(CHATS.ID)
                .doUpdate()
                .set(CHATS.API_URL, apiUrl)
                .set(CHATS.API_SESSION, session)
                .where(CHATS.ID.eq(chatId))
                .execute();
    }

    public Optional<ChatsRecord> getChat(long chatId) {
        return context.selectFrom(CHATS)
                .where(CHATS.ID.eq(chatId))
                .fetchOptional();
    }
}
