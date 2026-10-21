package com.ecommerce.assistant.conversation;

/** Un messaggio della conversazione: {@code role} vale "user" oppure "assistant". */
public record ChatTurn(String role, String text) {

    public static ChatTurn user(String text) {
        return new ChatTurn("user", text);
    }

    public static ChatTurn assistant(String text) {
        return new ChatTurn("assistant", text);
    }

    public boolean fromUser() {
        return "user".equals(role);
    }
}
