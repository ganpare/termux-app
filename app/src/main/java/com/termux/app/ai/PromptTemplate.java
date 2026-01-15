package com.termux.app.ai;

public class PromptTemplate {
    public String name;
    public String systemPrompt;

    public PromptTemplate(String name, String systemPrompt) {
        this.name = name;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public String toString() {
        return name; // For Spinner display
    }
}
