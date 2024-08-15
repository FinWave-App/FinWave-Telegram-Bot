package app.finwave.telegrambot.utils;

import app.finwave.api.AccountApi;
import app.finwave.api.NoteApi;
import app.finwave.api.TransactionApi;
import app.finwave.api.TransactionCategoryApi;
import app.finwave.api.tools.IRequest;
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
    
    public IRequest<?> parse(String clientRequest, long preferredAccountId) {
        if (clientRequest == null || clientRequest.isBlank())
            return null;

        if (clientRequest.startsWith("!"))
            return new NoteApi.NewNoteRequest(null, clientRequest.substring(1));

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

        String categoryWord = null;
        String accountWord = null;

        double maxCategorySimilarity = -1;
        double maxAccountSimilarity = -1;

        double categorySim = -1;
        double accountSim = -1;

        int deltaSig = delta.signum();

        List<TransactionCategoryApi.CategoryEntry> categories = state.getTransactionCategories().stream().filter((t) -> t.type() * deltaSig >= 0).toList();
        List<AccountApi.AccountEntry> accounts = state.getAccounts();

        TransactionCategoryApi.CategoryEntry targetCategory = null;
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
            for (var category : categories) {
                categorySim = similarity.apply(word.toLowerCase(), category.name().toLowerCase());

                boolean startWith = category.name().toLowerCase().startsWith(word.toLowerCase());

                if (startWith)
                    categorySim *= 1.2;

                if ((startWith || categorySim >= 0.5) && categorySim > maxCategorySimilarity) {
                    maxCategorySimilarity = categorySim;
                    targetCategory = category;
                    categoryWord = word;
                }

                if (categorySim >= 1)
                    break;
            }

            if (categorySim >= 1) break;
        }

        if (targetCategory == null)
            return null;

        words.remove(categoryWord);
        words.remove(accountWord);

        String description = null;

        if (!words.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            words.forEach((w) -> builder.append(w).append(" "));
            description = builder.toString().trim();
        }

        OffsetDateTime time = OffsetDateTime.now();

        return new TransactionApi.NewTransactionRequest(targetCategory.categoryId(), targetAccount.accountId(), time, delta, description);
    }
}
