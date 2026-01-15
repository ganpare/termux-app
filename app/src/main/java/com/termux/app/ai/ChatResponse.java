package com.termux.app.ai;

import java.util.List;

public class ChatResponse {
    public String id;
    public List<Choice> choices;

    public static class Choice {
        public int index;
        public ChatRequest.Message message;
        public String finish_reason;
    }
}
