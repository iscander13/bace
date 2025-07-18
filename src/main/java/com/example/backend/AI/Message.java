package com.example.backend.AI;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Message {
    private String sender; // "user" or "model" (for Gemini) / "assistant" (for OpenAI)
    private String text;
}
