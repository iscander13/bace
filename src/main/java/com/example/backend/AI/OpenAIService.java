package com.example.backend.AI;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest; // Исправлено на slf4j
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
public class OpenAIService {

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

        List<Object> messagesForOpenAI = new ArrayList<>();

        // Добавляем системное сообщение с контекстом полигона, если оно есть
        if (polygonContext != null && !polygonContext.trim().isEmpty()) {
            messagesForOpenAI.add(new Message("system", polygonContext)); // OpenAI API ожидает "system" для контекста
        }

        // Добавляем предыдущие сообщения из истории
        for (Message msg : history) {
            // Роли для OpenAI API: "user", "assistant", "system"
            String role = msg.getSender().equalsIgnoreCase("model") ? "assistant" : msg.getSender(); // Преобразуем "model" в "assistant"
            messagesForOpenAI.add(new Message(role, msg.getText()));
        }

        // Добавляем текущее сообщение пользователя
        messagesForOpenAI.add(new Message("user", userMessage));

        try {
            // Формируем тело запроса для OpenAI API
            // Используем "messages" вместо "contents" и добавляем "model"
            String requestBody = objectMapper.writeValueAsString(new OpenAIRequest("gpt-3.5-turbo", messagesForOpenAI)); // Используем gpt-3.5-turbo или другую модель
            
            log.info("OpenAIService: Отправка запроса в OpenAI API. URL: {}", openaiApiUrl);
            log.debug("OpenAIService: Тело запроса: {}", requestBody); // Логируем тело запроса

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(openaiApiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            log.info("OpenAIService: Получен ответ от OpenAI API. Статус: {}", response.statusCode());
            log.debug("OpenAIService: Полное тело ответа от AI: {}", responseBody); // Логируем полное тело ответа

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
                log.warn("OpenAIService: Не удалось извлечь текст из ответа AI. Ответ: {}", responseBody);
                return "Извините, не удалось получить ответ от AI. (Неверный формат ответа)";
            } else {
                log.error("OpenAIService: Ошибка при запросе к AI. Статус: {}, Тело: {}", response.statusCode(), responseBody);
                // Попытка извлечь сообщение об ошибке из ответа OpenAI
                try {
                    JsonNode errorNode = objectMapper.readTree(responseBody).path("error");
                    if (errorNode.isObject() && errorNode.has("message")) {
                        return "Извините, произошла ошибка при общении с AI: " + errorNode.path("message").asText();
                    }
                } catch (Exception parseError) {
                    log.error("OpenAIService: Ошибка при парсинге тела ошибки AI: {}", parseError.getMessage());
                }
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
