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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class WebSocketHandler extends RoutedWebSocketHandler {
    protected MainScene mainScene;
    protected ChatPreferenceDatabase preferenceDatabase;
    protected CompletableFuture<Boolean> authStatus = new CompletableFuture<>();

    public WebSocketHandler(MainScene mainScene, ChatPreferenceDatabase preferenceDatabase) {
        this.mainScene = mainScene;
        this.preferenceDatabase = preferenceDatabase;
    }

    public CompletableFuture<Boolean> getAuthStatus() {
        return authStatus;
    }

    @Override
    public void notifyUpdate(String s) {
        ChatHandler chatHandler = (ChatHandler) mainScene.getChatHandler();

        try {
            mainScene.updateState();
        } catch (ExecutionException | InterruptedException ignored) {}

        if (mainScene.equals(chatHandler.getActiveScene()))
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

        if (!scene.equals(chatHandler.getActiveScene())) {
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
        authStatus.complete(s.equals("Successful"));
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
