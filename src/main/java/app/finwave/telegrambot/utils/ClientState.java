package app.finwave.telegrambot.utils;

import app.finwave.api.*;
import app.finwave.api.tools.Transaction;
import app.finwave.api.tools.TransactionsFilter;
import com.google.common.util.concurrent.Futures;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

public class ClientState {
    protected FinWaveClient client;

    protected List<AccountTagApi.TagEntry> accountTags;
    protected List<AccountApi.AccountEntry> accounts;
    protected HashMap<Long, AccountTagApi.TagEntry> accountTagsMap;
    protected HashMap<Long, AccountApi.AccountEntry> accountsMap;

    protected List<TransactionTagApi.TagEntry> transactionTags;
    protected HashMap<Long, TransactionTagApi.TagEntry> transactionTagsMap;

    protected List<CurrencyApi.CurrencyEntry> currencies;
    protected HashMap<Long, CurrencyApi.CurrencyEntry> currenciesMap;

    public ClientState(FinWaveClient client) {
        this.client = client;
    }

    public CompletableFuture<Void> update() {
        return CompletableFuture.allOf(
                updateAccounts(),
                updateAccountTags(),
                updateCurrencies(),
                updateTransactionTags()
        );
    }

    public CompletableFuture<List<AccountTagApi.TagEntry>> updateAccountTags() {
        return client.runRequest(new AccountTagApi.GetTagsRequest())
                .thenApply(AccountTagApi.GetTagsResponse::tags).whenComplete((r, t) -> {
                    if (t != null) {
                        t.printStackTrace();
                        return;
                    }

                    accountTags = r;

                    HashMap<Long, AccountTagApi.TagEntry> newMap = new HashMap<>();
                    r.forEach((tag) -> newMap.put(tag.tagId(), tag));
                    accountTagsMap = newMap;
                });
    }

    public CompletableFuture<List<AccountApi.AccountEntry>> updateAccounts() {
        return client.runRequest(new AccountApi.GetAccountsRequest())
                .thenApply(AccountApi.GetAccountsListResponse::accounts).whenComplete((r, t) -> {
                    if (t != null) {
                        t.printStackTrace();
                        return;
                    }

                    accounts = r;

                    HashMap<Long, AccountApi.AccountEntry> newMap = new HashMap<>();
                    r.forEach((a) -> newMap.put(a.accountId(), a));
                    accountsMap = newMap;
                });
    }

    public CompletableFuture<List<TransactionTagApi.TagEntry>> updateTransactionTags() {
        return client.runRequest(new TransactionTagApi.GetTagsRequest())
                .thenApply(TransactionTagApi.GetTagsResponse::tags).whenComplete((r, t) -> {
                    if (t != null) {
                        t.printStackTrace();
                        return;
                    }

                    transactionTags = r;

                    HashMap<Long, TransactionTagApi.TagEntry> newMap = new HashMap<>();
                    r.forEach((tag) -> newMap.put(tag.tagId(), tag));
                    transactionTagsMap = newMap;
                });
    }

    public CompletableFuture<List<CurrencyApi.CurrencyEntry>> updateCurrencies() {
        return client.runRequest(new CurrencyApi.GetCurrenciesRequest())
                .thenApply(CurrencyApi.GetCurrenciesResponse::currencies).whenComplete((r, t) -> {
                    if (t != null) {
                        t.printStackTrace();
                        return;
                    }

                    currencies = r;

                    HashMap<Long, CurrencyApi.CurrencyEntry> newMap = new HashMap<>();
                    r.forEach((c) -> newMap.put(c.currencyId(), c));
                    currenciesMap = newMap;
                });
    }

    public CompletableFuture<List<Transaction>> fetchLastTransactions(int count) {
        return client.runRequest(new TransactionApi.GetTransactionsRequest(0, count, TransactionsFilter.EMPTY))
                .thenApply(TransactionApi.GetTransactionsListResponse::transactions);
    }

    public String formatAmount(BigDecimal amount, long accountId, boolean addPlus, boolean hide) {
        if (hide)
            return "▒▒▒▒";

        AccountApi.AccountEntry account = accountsMap.get(accountId);
        CurrencyApi.CurrencyEntry currency = currenciesMap.get(account.currencyId());

        DecimalFormat df = new DecimalFormat();
        StringBuilder builder = new StringBuilder();

        return builder
                .append(amount.signum() > 0 && addPlus ? "+" : "")
                .append(df.format(amount.setScale(currency.decimals(), RoundingMode.HALF_UP).doubleValue()))
                .append(currency.symbol())
                .toString();
    }

    public HashMap<AccountTagApi.TagEntry, ArrayList<AccountApi.AccountEntry>> getAccountsByTags() {
        HashMap<AccountTagApi.TagEntry, ArrayList<AccountApi.AccountEntry>> accountsByTags = new HashMap<>();

        accounts.forEach((a) -> {
            AccountTagApi.TagEntry tag = accountTagsMap.get(a.tagId());

            if (!accountsByTags.containsKey(tag))
                accountsByTags.put(tag, new ArrayList<>());

            accountsByTags.get(tag).add(a);
        });

        return accountsByTags;
    }

    public List<AccountTagApi.TagEntry> getAccountTags() {
        return Collections.unmodifiableList(accountTags);
    }

    public List<AccountApi.AccountEntry> getAccounts() {
        return Collections.unmodifiableList(accounts);
    }

    public List<TransactionTagApi.TagEntry> getTransactionTags() {
        return Collections.unmodifiableList(transactionTags);
    }

    public List<CurrencyApi.CurrencyEntry> getCurrencies() {
        return Collections.unmodifiableList(currencies);
    }


    public Map<Long, AccountApi.AccountEntry> getAccountsMap() {
        return Collections.unmodifiableMap(accountsMap);
    }

    public Map<Long, AccountTagApi.TagEntry> getAccountTagsMap() {
        return Collections.unmodifiableMap(accountTagsMap);
    }

    public Map<Long, CurrencyApi.CurrencyEntry> getCurrenciesMap() {
        return Collections.unmodifiableMap(currenciesMap);
    }

    public Map<Long, TransactionTagApi.TagEntry> getTransactionTagsMap() {
        return Collections.unmodifiableMap(transactionTagsMap);
    }
}
