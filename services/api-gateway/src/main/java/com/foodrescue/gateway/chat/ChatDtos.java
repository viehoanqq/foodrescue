package com.foodrescue.gateway.chat;

import java.util.List;

public final class ChatDtos {
    private ChatDtos() {}

    public record ChatRequest(String message, List<ChatTurn> history, Double latitude, Double longitude) {}

    public record ChatTurn(String role, String text) {}

    public record Suggestion(String type, Long id, String title, String subtitle, String path) {}

    public record ChatResponse(String reply, List<String> toolsUsed, String model,
                               boolean authenticated, List<Suggestion> suggestions) {}
}
