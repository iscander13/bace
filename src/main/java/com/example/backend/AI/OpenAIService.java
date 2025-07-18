package com.example.backend.AI;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class OpenAIService { // Переименован в OpenAIService, но может работать с Gemini

    @Value("${openai.api.key:}") // Ключ API OpenAI
    private String openaiApiKey;

    @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}") // URL API OpenAI
    private String openaiApiUrl;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Получает ответ от AI модели на основе сообщения пользователя и истории чата.
     *
     * @param userMessage Сообщение пользователя.
     * @param history История предыдущих сообщений (отправитель и текст).
     * @param polygonContext Контекст полигона, который будет добавлен как системное сообщение.
     * @return Ответ AI.
     */
    public String getChatCompletion(String userMessage, List<Message> history, String polygonContext) {
        log.info("OpenAIService: Получение ответа AI. Сообщение пользователя: {}", userMessage);
        log.debug("OpenAIService: История чата: {}", history);
        log.debug("OpenAIService: Контекст полигона: {}", polygonContext);

        List<Object> contents = new ArrayList<>();

        // Добавляем системное сообщение с контекстом полигона, если оно есть
        if (polygonContext != null && !polygonContext.trim().isEmpty()) {
            contents.add(new Message("user", polygonContext)); // OpenAI API ожидает "user" для системного контекста
            // contents.add(new Message("model", "OK. Я понял контекст.")); // Эта строка специфична для Gemini, удаляем для OpenAI
        }

        // Добавляем предыдущие сообщения из истории
        for (Message msg : history) {
            // Роли для OpenAI API: "user", "assistant", "system"
            String role = msg.getSender().equalsIgnoreCase("model") ? "assistant" : msg.getSender(); // Преобразуем "model" в "assistant"
            contents.add(new Message(role, msg.getText()));
        }

        // Добавляем текущее сообщение пользователя
        contents.add(new Message("user", userMessage));

        try {
            // Формируем тело запроса для OpenAI API
            // Используем "messages" вместо "contents" и добавляем "model"
            String requestBody = objectMapper.writeValueAsString(new OpenAIRequest("gpt-3.5-turbo", contents)); // Используем gpt-3.5-turbo или другую модель

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(openaiApiUrl)) // Используем openaiApiUrl
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + openaiApiKey) // Используем openaiApiKey
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            log.debug("OpenAIService: Ответ от AI: {}", responseBody);

            if (response.statusCode() == 200) {
                JsonNode rootNode = objectMapper.readTree(responseBody);
                JsonNode choices = rootNode.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    JsonNode messageNode = choices.get(0).path("message");
                    JsonNode contentNode = messageNode.path("content");
                    if (contentNode.isTextual()) {
                        return contentNode.asText();
                    }
                }
                log.warn("OpenAIService: Не удалось извлечь текст из ответа AI: {}", responseBody);
                return "Извините, не удалось получить ответ от AI. (Неверный формат ответа)";
            } else {
                log.error("OpenAIService: Ошибка при запросе к AI. Статус: {}, Тело: {}", response.statusCode(), responseBody);
                return "Извините, произошла ошибка при общении с AI: " + response.statusCode() + " - " + responseBody;
            }

        } catch (Exception e) {
            log.error("OpenAIService: Исключение при получении ответа AI: {}", e.getMessage(), e);
            return "Извините, произошла внутренняя ошибка при обработке запроса AI.";
        }
    }

    // Вспомогательные классы для сериализации запроса
    private static class OpenAIRequest {
        public String model;
        public List<Object> messages;

        public OpenAIRequest(String model, List<Object> messages) {
            this.model = model;
            this.messages = messages;
        }
    }
}
