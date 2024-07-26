package app.finwave.telegrambot.scenes;

import app.finwave.api.websocket.messages.response.notifications.Notification;
import app.finwave.tat.handlers.AbstractChatHandler;
import app.finwave.tat.menu.BaseMenu;
import app.finwave.tat.scene.BaseScene;
import app.finwave.tat.utils.MessageBuilder;
import app.finwave.telegrambot.database.ChatDatabase;
import app.finwave.telegrambot.handlers.ChatHandler;
import app.finwave.telegrambot.utils.EmojiList;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

public class NotificationScene extends BaseScene<Object> {
    protected ArrayList<Notification> notifications = new ArrayList<>();
    protected BaseMenu menu;

    public NotificationScene(AbstractChatHandler abstractChatHandler) {
        super(abstractChatHandler);
    }

    @Override
    public void start() {
        super.start();

        boolean needNewMessage = notifications.stream().anyMatch(n -> !n.options().silent());

        if (needNewMessage) {
            try {
                abstractChatHandler.deleteMessage(abstractChatHandler.getLastSentMessageId()).get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        menu = new BaseMenu(this, !needNewMessage);

        update();
    }

    protected void update() {
        menu.removeAllButtons();

        MessageBuilder builder = MessageBuilder.create();
        builder.line("Пришло уведомление:").gap();

        for (Notification notification : notifications) {
            builder.line(notification.text());
        }

        menu.setMessage(builder.build());

        menu.addButton(new InlineKeyboardButton("Прочитано " + EmojiList.ACCEPT), (e) -> {
            ChatHandler handler = (ChatHandler) abstractChatHandler;

            handler.stopActiveScene();
            handler.startScene("main");
        });

        menu.apply();
    }

    @Override
    public void stop() {
        super.stop();

        notifications.clear();
    }

    public void notify(Notification notification) {
        notifications.add(notification);

        ChatHandler handler = (ChatHandler) abstractChatHandler;
        if (this.equals(handler.getActiveScene()))
            update();
    }
}
