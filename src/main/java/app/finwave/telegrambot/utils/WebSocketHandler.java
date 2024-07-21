package app.finwave.telegrambot.utils;

import app.finwave.api.websocket.messages.handler.RoutedWebSocketHandler;
import app.finwave.api.websocket.messages.requests.SubscribeNotificationsRequest;
import app.finwave.api.websocket.messages.response.notifications.Notification;
import app.finwave.telegrambot.database.ChatPreferenceDatabase;
import app.finwave.telegrambot.handlers.ChatHandler;
import app.finwave.telegrambot.scenes.MainScene;
import app.finwave.telegrambot.scenes.NotificationScene;
import org.java_websocket.handshake.ServerHandshake;

import java.util.UUID;

public class WebSocketHandler extends RoutedWebSocketHandler {
    protected MainScene mainScene;
    protected ChatPreferenceDatabase preferenceDatabase;

    public WebSocketHandler(MainScene mainScene, ChatPreferenceDatabase preferenceDatabase) {
        this.mainScene = mainScene;
        this.preferenceDatabase = preferenceDatabase;
    }

    @Override
    public void notifyUpdate(String s) {
        ChatHandler chatHandler = (ChatHandler) mainScene.getChatHandler();

        if (chatHandler.getActiveScene().equals(mainScene))
            mainScene.update();
    }

    @Override
    public void genericMessage(String s, int i) {

    }

    @Override
    public void notification(Notification notification) {
        ChatHandler chatHandler = (ChatHandler) mainScene.getChatHandler();
        NotificationScene scene = chatHandler.getNotificationScene();

        scene.notify(notification);

        if (!chatHandler.getActiveScene().equals(scene)) {
            chatHandler.stopActiveScene();
            chatHandler.startScene("notification");
        }
    }

    @Override
    public void notificationPointRegistered(long l, UUID uuid) {
        preferenceDatabase.setNotificationUUID(mainScene.getChatHandler().getChatId(), uuid);

        client.send(new SubscribeNotificationsRequest(uuid));
    }

    @Override
    public void notificationPointSubscribe(String s) {

    }

    @Override
    public void authStatus(String s) {

    }

    @Override
    public void opened(ServerHandshake serverHandshake) {

    }

    @Override
    public void onError(Exception e) {

    }

    @Override
    public void closed(int i, String s, boolean b) {

    }
}
