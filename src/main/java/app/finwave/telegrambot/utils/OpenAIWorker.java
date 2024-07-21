package app.finwave.telegrambot.utils;

import app.finwave.api.AccountApi;
import app.finwave.api.CurrencyApi;
import app.finwave.api.TransactionApi;
import app.finwave.api.TransactionTagApi;
import app.finwave.api.tools.ApiException;
import app.finwave.api.tools.Transaction;
import app.finwave.api.tools.TransactionsFilter;
import app.finwave.tat.utils.Pair;
import app.finwave.tat.utils.Stack;
import app.finwave.telegrambot.config.ConfigWorker;
import app.finwave.telegrambot.config.OpenAIConfig;
import app.finwave.telegrambot.jooq.tables.records.ChatsPreferencesRecord;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.pengrad.telegrambot.model.Message;
import io.github.stefanbratanov.jvm.openai.*;
import org.jooq.meta.derby.sys.Sys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Singleton
public class OpenAIWorker {
    protected static final Logger log = LoggerFactory.getLogger(OpenAIWorker.class);

    protected OpenAI openAI;
    protected OpenAIConfig config;

    @Inject
    public OpenAIWorker(ConfigWorker configWorker) {
        this.config = configWorker.openAI;

        this.openAI = OpenAI.newBuilder(config.token).build();
    }

    protected String buildSystemMessage(ClientState state, ChatsPreferencesRecord record) {
        return config.systemMessage + "\n Here useful info:\n" + stateToText(state, record);
    }

    protected String askGPT(GPTContext context, String systemMessage) {
        log.trace("GPT request: {}", context);

        ChatClient chatClient = openAI.chatClient();

        var builder = CreateChatCompletionRequest.newBuilder()
                .model(config.model)
                .message(ChatMessage.systemMessage(systemMessage));

        var messages = new ArrayList<>(context.raw());

        Collections.reverse(messages);

        for (var message : messages) {
            if (message.first().equals("bot"))
                builder = builder.message(ChatMessage.assistantMessage(message.second()));
            else {
                builder = builder.message(ChatMessage.userMessage(message.second()));
            }
        }

        ChatCompletion chatCompletion = chatClient.createChatCompletion(builder.build());
        String response = chatCompletion.choices().get(0).message().content();

        log.trace("GPT response: {}", response);

        return response;
    }

    public String answer(GPTContext context, ClientState state, ChatsPreferencesRecord record, int maxRuns) {
        Pair<String, String> lastMessage = context.peek();

        if (maxRuns < 1) {
            log.warn("GPT out of max reruns: {}\nChat id: {}", context, record.getChatId());

            return "ChatGPT вышел за пределы допустимых обращений одиноразово";
        }

        if (lastMessage.first().equals("user")) {
            lastMessage = new Pair<>("bot",
                    askGPT(context, buildSystemMessage(state, record))
            );

            context.push("bot", lastMessage.second());
        }

        String[] lines = lastMessage.second().split("\n");

        String additionInfo = "";

        int createdTransactions = 0;
        int editedTransactions = 0;
        int deletedTransactions = 0;
        int failedParsing = 0;

        boolean justAnswer = true;

        for (String line : lines) {
            String[] words = line.split(" ");

            if (words.length < 2)
                continue;

            switch (words[0]) {
                case "GET_TRANSACTIONS" -> {
                    justAnswer = false;

                    try {
                        int count = Integer.parseInt(words[1]);
                        var response = state.client.runRequest(new TransactionApi.GetTransactionsRequest(0, count, TransactionsFilter.EMPTY)).get();

                        StringBuilder builder = new StringBuilder();
                        builder.append("Last Transactions:\n");

                        for (var transaction : response.transactions())
                            builder.append(transactionToGPTString(transaction)).append("\n");

                        if (response.transactions().isEmpty())
                            builder.append("EMPTY");

                        additionInfo = builder.toString();
                    }catch (NumberFormatException | InterruptedException | ExecutionException e) {
                        log.error("Error to parse gpt answer: {}", line, e);

                        failedParsing++;
                    }
                }
                case "NEW_TRANSACTION" -> {
                    if (words.length < 4) {
                        failedParsing++;
                        continue;
                    }

                    justAnswer = false;

                    try {
                        long tagId = Long.parseLong(words[1]);
                        long accountId = Long.parseLong(words[2]);
                        BigDecimal delta = new BigDecimal(words[3]);
                        String description = null;

                        if (words.length > 4)
                            description = arrayToStringFromIndex(words, 4);

                        var response = state.client.runRequest(new TransactionApi.NewTransactionRequest(tagId, accountId, OffsetDateTime.now(), delta, description)).get();

                        if (response != null && response.transactionId() > 0)
                            createdTransactions++;
                    }catch (NumberFormatException | InterruptedException | ExecutionException e) {
                        log.error("Error to parse gpt answer: {}", line, e);

                        failedParsing++;
                    }
                }
                case "EDIT_TRANSACTION" -> {
                    justAnswer = false;

                    try {
                        long transactionId = Long.parseLong(words[1]);
                        long tagId = Long.parseLong(words[2]);
                        long accountId = Long.parseLong(words[3]);
                        BigDecimal delta = new BigDecimal(words[4]);
                        String description = null;

                        if (words.length > 5)
                            description = arrayToStringFromIndex(words, 5);

                        var response = state.client.runRequest(new TransactionApi.EditTransactionRequest(transactionId, tagId, accountId, OffsetDateTime.now(), delta, description)).get();

                        if (response != null)
                            editedTransactions++;
                    }catch (NumberFormatException | InterruptedException | ExecutionException e) {
                        log.error("Error to parse gpt answer: {}", line, e);

                        failedParsing++;
                    }
                }
                case "DELETE_TRANSACTION" -> {
                    justAnswer = false;

                    try {
                        long transactionId = Long.parseLong(words[1]);

                        var response = state.client.runRequest(new TransactionApi.DeleteTransactionRequest(transactionId)).get();

                        if (response != null)
                            deletedTransactions++;
                    }catch (NumberFormatException | InterruptedException | ExecutionException e) {
                        log.error("Error to parse gpt answer: {}", line, e);

                        failedParsing++;
                    }
                }
                case "NEW_TRANSFER" -> {
                    justAnswer = false;

                    try {
                        long tagId = Long.parseLong(words[1]);
                        long fromAccountId = Long.parseLong(words[2]);
                        long toAccountId = Long.parseLong(words[3]);
                        BigDecimal fromDelta = new BigDecimal(words[4]);
                        BigDecimal toDelta = new BigDecimal(words[5]);

                        String description = null;

                        if (words.length > 6)
                            description = arrayToStringFromIndex(words, 6);

                        var response = state.client.runRequest(new TransactionApi.NewInternalTransferRequest(tagId, fromAccountId, toAccountId, OffsetDateTime.now(), fromDelta, toDelta, description)).get();

                        if (response != null)
                            createdTransactions++;
                    }catch (NumberFormatException | InterruptedException | ExecutionException e) {
                        log.error("Error to parse gpt answer: {}", line, e);

                        failedParsing++;
                    }
                }
            }
        }

        if (createdTransactions > 0) {
            context.push("bot", "CREATED TRANSACTIONS: " + createdTransactions);
        }

        if (editedTransactions > 0) {
            context.push("bot", "EDITED TRANSACTIONS: " + editedTransactions);
        }

        if (deletedTransactions > 0) {
            context.push("bot", "DELETED TRANSACTIONS: " + deletedTransactions);
        }

        if (failedParsing > 0){
            context.push("bot", "FAILED PARSING OR API ERROR: " + failedParsing);
        }

        if (!additionInfo.isBlank())
            context.push("bot", additionInfo);

        if (justAnswer) {
            return context.peek().second();
        }

        lastMessage = new Pair<>("bot",
                askGPT(context, buildSystemMessage(state, record))
        );

        context.push("bot", lastMessage.second());

        return answer(context, state, record, maxRuns-1);
    }

    public static String arrayToStringFromIndex(String[] array, int startIndex) {
        if (array == null || startIndex < 0 || startIndex >= array.length) {
            throw new IllegalArgumentException("Invalid input");
        }

        StringBuilder result = new StringBuilder();
        for (int i = startIndex; i < array.length; i++) {
            result.append(array[i]);

            if (i < array.length - 1) {
                result.append(' ');
            }
        }

        return result.toString();
    }

    protected String transactionToGPTString(Transaction transaction) {
        StringBuilder builder = new StringBuilder();

        builder.append("id: ").append(transaction.transactionId())
                .append(", delta: ").append(transaction.delta().toString())
                .append(", account id: ").append(transaction.accountId())
                .append(", transaction tag id: ").append(transaction.tagId())
                .append(", description: ").append(transaction.description())
                .append(", created: ").append(transaction.createdAt().toString())
                .append(", currency id: ").append(transaction.currencyId());

        return builder.toString();
    }

    protected String stateToText(ClientState state, ChatsPreferencesRecord preferencesRecord) {
        StringBuilder builder = new StringBuilder();

        List<AccountApi.AccountEntry> accounts = state.getAccounts();
        List<TransactionTagApi.TagEntry> transactionTags = state.getTransactionTags();
        List<CurrencyApi.CurrencyEntry> currencies = state.getCurrencies();
        Map<Long, CurrencyApi.CurrencyEntry> currenciesMap = state.getCurrenciesMap();

        long preferredAccountId = preferencesRecord.getPreferredAccountId();

        builder.append("Accounts:").append("\n");

        for (var account : accounts)
            builder.append("id: ").append(account.accountId())
                    .append(", name: ").append(account.name())
                    .append(", description: ").append(account.description())
                    .append(", amount: ").append(state.formatAmount(account.amount(), account.accountId(), false, false))
                    .append(", currency: ").append(currenciesMap.get(account.currencyId()).code()).append(" (").append(account.currencyId()).append(")")
                    .append(", hidden: ").append(account.hidden() ? "yes" : "no")
                    .append("\n");

        builder.append("Transactions Tags:").append("\n");

        for (var tag : transactionTags)
            builder.append("id: ").append(tag.tagId())
                    .append(", name: ").append(tag.name())
                    .append(", description: ").append(tag.description())
                    .append(", type: ").append(tag.type() == 1 ? "only incomes" : (tag.type() == 0 ? "incomes and expanses" : "only expanses"))
                    .append("\n");

        builder.append("Currencies Tags:").append("\n");

        for (var currency : currencies)
            builder.append("id: ").append(currency.currencyId())
                    .append(", symbol: ").append(currency.symbol())
                    .append(", code: ").append(currency.code())
                    .append(", description: ").append(currency.description())
                    .append(", can edit: ").append(currency.owned() ? "yes" : "no")
                    .append(", decimals: ").append(currency.decimals())
                    .append("\n");

        if (preferredAccountId > 0)
            builder.append("Preferred account id: ").append(preferencesRecord.getPreferredAccountId());

        return builder.toString();
    }
}
