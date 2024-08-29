package app.finwave.telegrambot.utils;

import app.finwave.api.*;
import app.finwave.api.tools.IRequest;
import app.finwave.tat.utils.Pair;
import org.apache.commons.text.similarity.JaccardSimilarity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ActionParser {
    protected ClientState state;

    protected JaccardSimilarity similarity = new JaccardSimilarity();

    public ActionParser(ClientState state) {
        this.state = state;
    }

    protected Pair<AccountApi.AccountEntry, Integer> findAccount(List<String> words, int deltaIndex, long preferredAccountId) {
        AccountApi.AccountEntry targetAccount = null;

        List<AccountApi.AccountEntry> accounts = state.getAccounts();
        Map<Long, AccountFolderApi.FolderEntry> folderMap = state.getAccountFoldersMap();

        double maxAccountSimilarity = -1;
        double accountSim;
        int accountAdditionalWords = 0;

        for (var account : accounts) {
            AccountFolderApi.FolderEntry folder = folderMap.get(account.folderId());
            int additionalWords = folder.name().trim().split(" ").length +
                    account.name().trim().split(" ").length + 1;

            String closetWords = String.join(" ", words.subList(
                    Math.max(0, deltaIndex - additionalWords),
                    Math.min(words.size(), deltaIndex + additionalWords + 1)
            )).toLowerCase();

            accountSim = similarity.apply(closetWords, (folder.name() + " " + account.name()).toLowerCase());

            if (account.accountId() == preferredAccountId)
                accountSim *= 1.2;

            if (accountSim > maxAccountSimilarity) {
                maxAccountSimilarity = accountSim;
                targetAccount = account;
                accountAdditionalWords = additionalWords;
            }
        }

        return Pair.of(targetAccount, accountAdditionalWords);
    }

    protected TransactionCategoryApi.CategoryEntry findCategory(ArrayList<String> words, int deltaIndex, int deltaSig, int accountAdditionalWords) {
        TransactionCategoryApi.CategoryEntry targetCategory = null;

        List<TransactionCategoryApi.CategoryEntry> categories = state.getTransactionCategories().stream().filter((t) -> t.type() * deltaSig >= 0).toList();
        double maxCategorySimilarity = -1;
        double categorySim;

        for (var category : categories) {
            int additionalWords = accountAdditionalWords + category.name().trim().split(" ").length;

            String closetWords = String.join(" ", words.subList(
                    Math.max(0, deltaIndex - additionalWords),
                    Math.min(words.size(), deltaIndex + additionalWords + 1)
            )).toLowerCase();

            categorySim = similarity.apply(closetWords, category.name().toLowerCase());

            if (categorySim > maxCategorySimilarity) {
                maxCategorySimilarity = categorySim;
                targetCategory = category;
            }
        }

        return targetCategory;
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

        if (delta == null || words.isEmpty())
            return null;

        AccountApi.AccountEntry targetAccount;
        int additionalWords = 0;

        if (words.size() == 1) {
            targetAccount = state.getAccountsMap().get(preferredAccountId);
        }else {
            Pair<AccountApi.AccountEntry, Integer> found = findAccount(words, deltaIndex, preferredAccountId);
            targetAccount = found.first();
            additionalWords = found.second();
        }

        if (targetAccount == null)
            return null;

        TransactionCategoryApi.CategoryEntry targetCategory = findCategory(words, deltaIndex, delta.signum(), additionalWords);

        if (targetCategory == null)
            return null;

        return new TransactionApi.NewTransactionRequest(
                targetCategory.categoryId(),
                targetAccount.accountId(),
                OffsetDateTime.now(),
                delta,
                String.join(" ", words) + " (TG)"
        );
    }
}
