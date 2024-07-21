package app.finwave.telegrambot.utils;

public enum GPTMode {
    ALWAYS(1, "Всегда"),
    ON_UNCERTAIN(0, "Ситуативно"),
    DISABLED(-1, "Отключено");

    public final int mode;
    public final String name;

    GPTMode(int mode, String name) {
        this.mode = mode;
        this.name = name;
    }

    public static GPTMode of(int mode) {
        return switch (mode) {
            case 1 -> ALWAYS;
            case 0 -> ON_UNCERTAIN;
            default -> DISABLED;
        };

    }
}
