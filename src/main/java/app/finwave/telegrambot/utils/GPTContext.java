package app.finwave.telegrambot.utils;

import app.finwave.tat.utils.Pair;
import app.finwave.tat.utils.Stack;
import com.pengrad.telegrambot.model.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GPTContext extends Stack<Pair<String, String>> {
    public GPTContext(int maxSize) {
        super(maxSize);
    }

    public void push(String role, String message) {
        push(new Pair<>(role, message));
    }

    public void push(Message message) {
        push(message.from().isBot() ? "bot" : "user", message.text());
    }
}
