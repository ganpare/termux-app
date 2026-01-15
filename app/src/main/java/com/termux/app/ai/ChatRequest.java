package com.termux.app.ai;

import java.util.List;

public class ChatRequest {
    public String model;
    public List<Message> messages;
    public boolean stream;

    public ChatRequest(String model, List<Message> messages, boolean stream) {
        this.model = model;
        this.messages = messages;
        this.stream = stream;
    }

    public static class Message {
        public String role;
        public String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}
