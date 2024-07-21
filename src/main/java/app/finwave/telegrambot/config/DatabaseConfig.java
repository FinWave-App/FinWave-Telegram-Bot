package app.finwave.telegrambot.config;

import java.util.Optional;

public class DatabaseConfig {
    public String url = "jdbc:postgresql://postgres:5432/finwavebot";
    public String user = "finwavebot";
    public String password = Optional
            .ofNullable(System.getenv("DATABASE_PASSWORD"))
            .orElse("change_me");
}
