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

    protected List<AccountTagApi.TagEntry> accountTags = new ArrayList<>();
    protected List<AccountApi.AccountEntry> accounts = new ArrayList<>();
    protected HashMap<Long, AccountTagApi.TagEntry> accountTagsMap = new HashMap<>();
    protected HashMap<Long, AccountApi.AccountEntry> accountsMap = new HashMap<>();

    protected List<TransactionTagApi.TagEntry> transactionTags  = new ArrayList<>();
    protected HashMap<Long, TransactionTagApi.TagEntry> transactionTagsMap = new HashMap<>();

    protected List<CurrencyApi.CurrencyEntry> currencies = new ArrayList<>();
    protected HashMap<Long, CurrencyApi.CurrencyEntry> currenciesMap = new HashMap<>();

    protected ReentrantLock accountsLock = new ReentrantLock();
    protected ReentrantLock accountTagsLock = new ReentrantLock();
    protected ReentrantLock transactionTagsLock = new ReentrantLock();
    protected ReentrantLock currenciesLock = new ReentrantLock();

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

                    HashMap<Long, AccountTagApi.TagEntry> newMap = new HashMap<>();
                    r.forEach((tag) -> newMap.put(tag.tagId(), tag));

                    accountTagsLock.lock();

                    accountTags = r;
                    accountTagsMap = newMap;

                    accountTagsLock.unlock();
                });
    }

    public CompletableFuture<List<AccountApi.AccountEntry>> updateAccounts() {
        return client.runRequest(new AccountApi.GetAccountsRequest())
                .thenApply(AccountApi.GetAccountsListResponse::accounts).whenComplete((r, t) -> {
                    if (t != null) {
                        t.printStackTrace();
                        return;
                    }

                    HashMap<Long, AccountApi.AccountEntry> newMap = new HashMap<>();
                    r.forEach((a) -> newMap.put(a.accountId(), a));

                    accountsLock.lock();

                    accounts = r;
                    accountsMap = newMap;

                    accountsLock.unlock();
                });
    }

    public CompletableFuture<List<TransactionTagApi.TagEntry>> updateTransactionTags() {
        return client.runRequest(new TransactionTagApi.GetTagsRequest())
                .thenApply(TransactionTagApi.GetTagsResponse::tags).whenComplete((r, t) -> {
                    if (t != null) {
                        t.printStackTrace();
                        return;
                    }

                    HashMap<Long, TransactionTagApi.TagEntry> newMap = new HashMap<>();
                    r.forEach((tag) -> newMap.put(tag.tagId(), tag));

                    transactionTagsLock.lock();

                    transactionTags = r;
                    transactionTagsMap = newMap;

                    transactionTagsLock.unlock();
                });
    }

    public CompletableFuture<List<CurrencyApi.CurrencyEntry>> updateCurrencies() {
        return client.runRequest(new CurrencyApi.GetCurrenciesRequest())
                .thenApply(CurrencyApi.GetCurrenciesResponse::currencies).whenComplete((r, t) -> {
                    if (t != null) {
                        t.printStackTrace();
                        return;
                    }

                    HashMap<Long, CurrencyApi.CurrencyEntry> newMap = new HashMap<>();
                    r.forEach((c) -> newMap.put(c.currencyId(), c));

                    currenciesLock.lock();

                    currencies = r;
                    currenciesMap = newMap;

                    currenciesLock.unlock();
                });
    }

    public CompletableFuture<List<Transaction>> fetchLastTransactions(int count) {
        return client.runRequest(new TransactionApi.GetTransactionsRequest(0, count, TransactionsFilter.EMPTY))
                .thenApply(TransactionApi.GetTransactionsListResponse::transactions);
    }

    public String formatAmount(BigDecimal amount, long accountId, boolean addPlus, boolean hide) {
        if (hide)
            return "▒▒▒▒";

        AccountApi.AccountEntry account;
        CurrencyApi.CurrencyEntry currency;

        accountsLock.lock();
        currenciesLock.lock();

        try {
            account = accountsMap.get(accountId);
            currency = currenciesMap.get(account.currencyId());
        }finally {
            accountsLock.unlock();
            currenciesLock.unlock();
        }

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

        accountsLock.lock();
        accountTagsLock.lock();

        try {
            accounts.forEach((a) -> {
                AccountTagApi.TagEntry tag = accountTagsMap.get(a.tagId());

                if (!accountsByTags.containsKey(tag))
                    accountsByTags.put(tag, new ArrayList<>());

                accountsByTags.get(tag).add(a);
            });
        }finally {
            accountsLock.unlock();
            accountTagsLock.unlock();
        }

        return accountsByTags;
    }

    public List<AccountTagApi.TagEntry> getAccountTags() {
        accountTagsLock.lock();

        try {
            return Collections.unmodifiableList(accountTags);
        }finally {
            accountTagsLock.unlock();
        }
    }

    public List<AccountApi.AccountEntry> getAccounts() {
        accountsLock.lock();

        try {
            return Collections.unmodifiableList(accounts);
        }finally {
            accountsLock.unlock();
        }
    }

    public List<TransactionTagApi.TagEntry> getTransactionTags() {
        transactionTagsLock.lock();

        try {
            return Collections.unmodifiableList(transactionTags);
        }finally {
            transactionTagsLock.unlock();
        }
    }

    public Map<Long, AccountApi.AccountEntry> getAccountsMap() {
        accountsLock.lock();

        try {
            return Collections.unmodifiableMap(accountsMap);
        }finally {
            accountsLock.unlock();
        }
    }

    public Map<Long, AccountTagApi.TagEntry> getAccountTagsMap() {
        accountTagsLock.lock();

        try {
            return Collections.unmodifiableMap(accountTagsMap);
        }finally {
            accountTagsLock.unlock();
        }
    }

    public List<CurrencyApi.CurrencyEntry> getCurrencies() {
        currenciesLock.lock();

        try {
            return Collections.unmodifiableList(currencies);
        }finally {
            currenciesLock.unlock();
        }
    }

    public Map<Long, CurrencyApi.CurrencyEntry> getCurrenciesMap() {
        currenciesLock.lock();

        try {
            return Collections.unmodifiableMap(currenciesMap);
        }finally {
            currenciesLock.unlock();
        }
    }

    public Map<Long, TransactionTagApi.TagEntry> getTransactionTagsMap() {
        transactionTagsLock.lock();

        try {
            return Collections.unmodifiableMap(transactionTagsMap);
        }finally {
            transactionTagsLock.unlock();
        }
    }
}
