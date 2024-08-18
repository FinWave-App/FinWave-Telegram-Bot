package app.finwave.telegrambot.utils;

import app.finwave.api.AiApi;
import app.finwave.api.FilesApi;
import app.finwave.api.FinWaveClient;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class AiWorker {
    protected FinWaveClient client;
    protected CompletableFuture<Long> currentContextId;
    protected long preferredAccountId;

    public AiWorker(FinWaveClient client, long preferredAccountId) {
        this.client = client;
        this.preferredAccountId = preferredAccountId;

        initContext();
    }

    public void setPreferredAccountId(long preferredAccountId) {
        this.preferredAccountId = preferredAccountId;
    }

    public void initContext() {
        currentContextId = client
                .runRequest(new AiApi.NewContextRequest("User's preferred account: " + preferredAccountId + ". If the user wants to add a transaction without specifying which account, then use this id."))
                .thenApply(AiApi.NewContextResponse::contextId);
    }

    public Optional<Long> getCurrentContextId() {
        try {
            return Optional.ofNullable(currentContextId.get());
        } catch (InterruptedException | ExecutionException ignored) { }

        return Optional.empty();
    }

    public CompletableFuture<String> ask(String message) {
        long context = getCurrentContextId().orElseThrow();

        return client.runRequest(new AiApi.AskRequest(context, message), 2 * 60 * 1000, 2 * 60 * 1000)
                .thenApply(AiApi.AnswerResponse::answer);
    }

    public CompletableFuture<Boolean> appendFile(String telegramUrl, String mime, String name) {
        long context = getCurrentContextId().orElseThrow();

        return client.runRequest(
                new FilesApi.UploadFromURLRequest(1, true, mime, name, null, telegramUrl)
        ).thenApply((response -> {
            try {
                return client.runRequest(new AiApi.AttachFileRequest(context, response.fileId())).get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();

                return null;
            }
        })).thenApply(
                (r) -> r != null && r.message().equals("Attached successfully")
        );
    }
}
