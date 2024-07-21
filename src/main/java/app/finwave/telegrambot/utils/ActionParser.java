package app.finwave.telegrambot.utils;

import app.finwave.api.AccountApi;
import app.finwave.api.TransactionApi;
import app.finwave.api.TransactionTagApi;
import org.apache.commons.text.similarity.JaccardSimilarity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ActionParser {
    protected ClientState state;

    protected JaccardSimilarity similarity = new JaccardSimilarity();

    public ActionParser(ClientState state) {
        this.state = state;
    }
    
    public TransactionApi.NewTransactionRequest parse(String clientRequest, long preferredAccountId) {
        if (clientRequest == null || clientRequest.isBlank())
            return null;

        ArrayList<String> words = new ArrayList<>(Arrays.asList(clientRequest.split(" ")));

        BigDecimal delta = null;
        int deltaIndex = 0;

        for (String word : words) {
            try {
                delta = new BigDecimal(word.replace(',', '.'));
                if (delta.signum() == 0)
                    continue;

                words.remove(deltaIndex);

                if (!word.startsWith("-") && !word.startsWith("+")) {
                    delta = delta.negate();
                }

                break;
            }catch (NumberFormatException ignored) {

            }

            deltaIndex++;
        }

        if (delta == null)
            return null;

        ArrayList<String> closetWords = new ArrayList<>(words.subList(
                Math.max(0, deltaIndex - 2),
                Math.min(words.size(), deltaIndex + 3)
        ));

        String tagWord = null;
        String accountWord = null;

        double maxTagSimilarity = -1;
        double maxAccountSimilarity = -1;

        double tagSim = -1;
        double accountSim = -1;

        int deltaSig = delta.signum();

        List<TransactionTagApi.TagEntry> tags = state.getTransactionTags().stream().filter((t) -> t.type() * deltaSig >= 0).toList();
        List<AccountApi.AccountEntry> accounts = state.getAccounts();

        TransactionTagApi.TagEntry targetTag = null;
        AccountApi.AccountEntry targetAccount = null;

        for (String word : closetWords) {
            for (var account : accounts) {
                accountSim = similarity.apply(word.toLowerCase(), account.name().toLowerCase());

                boolean startWith = account.name().toLowerCase().startsWith(word.toLowerCase());

                if (startWith)
                    accountSim *= 1.2;

                if ((startWith || accountSim >= 0.5) && accountSim > maxAccountSimilarity) {
                    maxAccountSimilarity = accountSim;
                    targetAccount = account;
                    accountWord = word;
                }

                if (accountSim >= 1)
                    break;
            }

            if (accountSim >= 1) break;
        }

        if (targetAccount == null || (preferredAccountId != -1 && accountSim < 0.95))
            targetAccount = state.getAccountsMap().get(preferredAccountId);

        if (targetAccount == null)
            return null;

        if (accountWord != null)
            closetWords.remove(accountWord);

        for (String word : closetWords) {
            for (var tag : tags) {
                tagSim = similarity.apply(word.toLowerCase(), tag.name().toLowerCase());

                boolean startWith = tag.name().toLowerCase().startsWith(word.toLowerCase());

                if (startWith)
                    tagSim *= 1.2;

                if ((startWith || tagSim >= 0.5) && tagSim > maxTagSimilarity) {
                    maxTagSimilarity = tagSim;
                    targetTag = tag;
                    tagWord = word;
                }

                if (tagSim >= 1)
                    break;
            }

            if (tagSim >= 1) break;
        }

        if (targetTag == null)
            return null;

        words.remove(tagWord);
        words.remove(accountWord);

        String description = null;

        if (!words.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            words.forEach((w) -> builder.append(w).append(" "));
            description = builder.toString().trim();
        }

        OffsetDateTime time = OffsetDateTime.now();

        return new TransactionApi.NewTransactionRequest(targetTag.tagId(), targetAccount.accountId(), time, delta, description);
    }
}
