package app.finwave.telegrambot.utils;

import app.finwave.api.*;
import app.finwave.api.tools.Transaction;
import app.finwave.api.tools.TransactionsFilter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;

public class ClientState {
    protected FinWaveClient client;

    protected ConfigApi.PublicConfigs configs;
    protected CompletableFuture<ConfigApi.PublicConfigs> configsFuture;

    protected List<AccountFolderApi.FolderEntry> accountFolders = new ArrayList<>();
    protected List<AccountApi.AccountEntry> accounts = new ArrayList<>();
    protected HashMap<Long, AccountFolderApi.FolderEntry> accountFoldersMap = new HashMap<>();
    protected HashMap<Long, AccountApi.AccountEntry> accountsMap = new HashMap<>();

    protected List<TransactionCategoryApi.CategoryEntry> transactionCategories = new ArrayList<>();
    protected HashMap<Long, TransactionCategoryApi.CategoryEntry> transactionCategoriesMap = new HashMap<>();

    protected List<CurrencyApi.CurrencyEntry> currencies = new ArrayList<>();
    protected HashMap<Long, CurrencyApi.CurrencyEntry> currenciesMap = new HashMap<>();

    protected ReentrantLock accountsLock = new ReentrantLock();
    protected ReentrantLock accountFoldersLock = new ReentrantLock();
    protected ReentrantLock transactionCategoriesLock = new ReentrantLock();
    protected ReentrantLock currenciesLock = new ReentrantLock();

    public ClientState(FinWaveClient client) {
        this.client = client;

        this.configsFuture = client.runRequest(new ConfigApi.GetConfigsRequest());
    }

    public CompletableFuture<Void> update() {
        return CompletableFuture.allOf(
                updateAccounts(),
                updateAccountFolders(),
                updateCurrencies(),
                updateTransactionCategories()
        );
    }

    public Optional<ConfigApi.PublicConfigs> getConfigs() {
        if (configs == null && !configsFuture.isCompletedExceptionally()) {
            try {
                configs = configsFuture.get();
            } catch (InterruptedException | ExecutionException ignored) { }
        }

        return Optional.ofNullable(configs);
    }

    public CompletableFuture<List<AccountFolderApi.FolderEntry>> updateAccountFolders() {
        return client.runRequest(new AccountFolderApi.GetFoldersRequest())
                .thenApply(AccountFolderApi.GetFoldersResponse::folders).whenComplete((r, t) -> {
                    if (t != null) {
                        t.printStackTrace();
                        return;
                    }

                    HashMap<Long, AccountFolderApi.FolderEntry> newMap = new HashMap<>();
                    r.forEach((f) -> newMap.put(f.folderId(), f));

                    accountFoldersLock.lock();

                    accountFolders = r;
                    accountFoldersMap = newMap;

                    accountFoldersLock.unlock();
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

    public CompletableFuture<List<TransactionCategoryApi.CategoryEntry>> updateTransactionCategories() {
        return client.runRequest(new TransactionCategoryApi.GetCategoriesRequest())
                .thenApply(TransactionCategoryApi.GetCategoriesResponse::categories).whenComplete((r, t) -> {
                    if (t != null) {
                        t.printStackTrace();
                        return;
                    }

                    HashMap<Long, TransactionCategoryApi.CategoryEntry> newMap = new HashMap<>();
                    r.forEach((c) -> newMap.put(c.categoryId(), c));

                    transactionCategoriesLock.lock();

                    transactionCategories = r;
                    transactionCategoriesMap = newMap;

                    transactionCategoriesLock.unlock();
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

    public CompletableFuture<List<NoteApi.NoteEntry>> fetchImportantNotes() {
        return client.runRequest(new NoteApi.GetImportantNotesRequest())
                .thenApply(NoteApi.GetNotesListResponse::notes);
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

    public HashMap<AccountFolderApi.FolderEntry, ArrayList<AccountApi.AccountEntry>> getAccountsByTags() {
        HashMap<AccountFolderApi.FolderEntry, ArrayList<AccountApi.AccountEntry>> accountsByFolders = new HashMap<>();

        accountsLock.lock();
        accountFoldersLock.lock();

        try {
            accounts.forEach((a) -> {
                AccountFolderApi.FolderEntry folder = accountFoldersMap.get(a.folderId());

                if (!accountsByFolders.containsKey(folder))
                    accountsByFolders.put(folder, new ArrayList<>());

                accountsByFolders.get(folder).add(a);
            });
        }finally {
            accountsLock.unlock();
            accountFoldersLock.unlock();
        }

        return accountsByFolders;
    }

    public List<AccountFolderApi.FolderEntry> getAccountFolders() {
        accountFoldersLock.lock();

        try {
            return Collections.unmodifiableList(accountFolders);
        }finally {
            accountFoldersLock.unlock();
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

    public List<TransactionCategoryApi.CategoryEntry> getTransactionCategories() {
        transactionCategoriesLock.lock();

        try {
            return Collections.unmodifiableList(transactionCategories);
        }finally {
            transactionCategoriesLock.unlock();
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

    public Map<Long, AccountFolderApi.FolderEntry> getAccountFoldersMap() {
        accountFoldersLock.lock();

        try {
            return Collections.unmodifiableMap(accountFoldersMap);
        }finally {
            accountFoldersLock.unlock();
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

    public Map<Long, TransactionCategoryApi.CategoryEntry> getTransactionCategoriesMap() {
        transactionCategoriesLock.lock();

        try {
            return Collections.unmodifiableMap(transactionCategoriesMap);
        }finally {
            transactionCategoriesLock.unlock();
        }
    }
}
