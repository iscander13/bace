package com.example.backend.controller;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.JWT.JwtService;
import com.example.backend.dto.ChatMessageDto;
import com.example.backend.entiity.ChatMessage;
import com.example.backend.entiity.PolygonArea;
import com.example.backend.entiity.User;
import com.example.backend.repository.ChatMessageRepository;
import com.example.backend.repository.PolygonAreaRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.service.ChatService;
import com.example.backend.service.PolygonService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@RestController
@RequestMapping("/api")
public class ChatController {

    @Value("${openai.api.key}")
    private String openaiApiKey;

    @Value("${openai.api.url}")
    private String openaiApiUrl;

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final PolygonService polygonService;
    private final JwtService jwtService;
    private final ChatMessageRepository chatMessageRepository;
    private final PolygonAreaRepository polygonAreaRepository;
    private final UserRepository userRepository;
    private final ChatService chatService;

    public ChatController(PolygonService polygonService, JwtService jwtService,
                          ChatMessageRepository chatMessageRepository,
                          PolygonAreaRepository polygonAreaRepository,
                          UserRepository userRepository,
                          ChatService chatService) {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
        this.polygonService = polygonService;
        this.jwtService = jwtService;
        this.chatMessageRepository = chatMessageRepository;
        this.polygonAreaRepository = polygonAreaRepository;
        this.userRepository = userRepository;
        this.chatService = chatService;
    }

    // Получение истории чата для полигона
    @GetMapping("/chat/polygons/{polygonId}")
    public ResponseEntity<?> getPolygonChatHistory(@PathVariable UUID polygonId,
                                                   @AuthenticationPrincipal User principalUser) {
        if (principalUser == null || principalUser.getUsername() == null) {
            return new ResponseEntity<>(
                    Collections.singletonMap("error", "Пользователь не аутентифицирован или email не найден."),
                    HttpStatus.UNAUTHORIZED
                );
        }

        try {
            // Делегируем получение истории чата сервисному слою.
            // ChatService сам определяет, нужно ли обращаться к БД для DEMO-пользователей.
            List<ChatMessage> messages = chatService.getChatMessagesByPolygonId(polygonId.toString());
            
            List<ChatMessageDto> messageDtos = messages.stream()
                    .map(msg -> ChatMessageDto.builder()
                            .id(msg.getId())
                            .sender(msg.getSender())
                            .text(msg.getText())
                            .timestamp(msg.getTimestamp())
                            .build())
                    .collect(Collectors.toList());

            return new ResponseEntity<>(messageDtos, HttpStatus.OK);
        } catch (SecurityException e) {
            return new ResponseEntity<>(
                    Collections.singletonMap("error", e.getMessage()),
                    HttpStatus.FORBIDDEN
            );
        } catch (RuntimeException e) {
            return new ResponseEntity<>(
                    Collections.singletonMap("error", e.getMessage()),
                    HttpStatus.NOT_FOUND
            );
        } catch (Exception e) {
            return new ResponseEntity<>(
                    Collections.singletonMap("error", "Произошла непредвиденная ошибка при получении истории чата."),
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }


    // Обработка сообщения чата для полигона
    @PostMapping("/chat/polygons/{polygonId}/messages")
    public ResponseEntity<?> handlePolygonChatMessage(@PathVariable UUID polygonId, @RequestBody Map<String, Object> payload,
                                                      @AuthenticationPrincipal User principalUser) {
        String userMessage = (String) payload.get("message");
        // История теперь приходит в формате List<Map<String, String>> с полями "role" и "content"
        List<Map<String, String>> history = (List<Map<String, String>>) payload.get("history"); 
        
        if (principalUser == null || principalUser.getUsername() == null) {
            System.err.println("Ошибка: Пользователь не аутентифицирован или Principal не является User.");
            return new ResponseEntity<>(
                    Collections.singletonMap("error", "Пользователь не аутентифицирован."),
                    HttpStatus.UNAUTHORIZED
            );
        }

        // Получаем текущего пользователя через ChatService (он сам обработает DEMO/USER)
        User currentUser = chatService.getCurrentAuthenticatedUser();

        if (userMessage == null || userMessage.trim().isEmpty()) {
            return new ResponseEntity<>(
                    Collections.singletonMap("error", "Сообщение не предоставлено."),
                    HttpStatus.BAD_REQUEST
            );
        }

        try {
            // Сохраняем сообщение пользователя через ChatService.
            // ChatService сам определяет, сохранять ли в БД в зависимости от роли (для DEMO - не сохраняет).
            chatService.saveChatMessage(polygonId.toString(), "user", userMessage);

            // --- Логика формирования контекста полигона для ИИ ---
            String polygonContext = "";
            Optional<PolygonArea> polygonOptional = Optional.empty();
            if (!"DEMO".equalsIgnoreCase(currentUser.getRole())) {
                 polygonOptional = polygonAreaRepository.findById(polygonId);
            }

            if (polygonOptional.isPresent()) {
                PolygonArea polygon = polygonOptional.get();
                polygonContext = String.format(
                    "Ты работаешь с полигоном. Вот его данные: Название: \"%s\", Культура: \"%s\". Комментарий: \"%s\".",
                    polygon.getName(),
                    polygon.getCrop(),
                    polygon.getComment() != null ? polygon.getComment() : "нет"
                );
                if (polygon.getGeoJson() != null && !polygon.getGeoJson().isEmpty()) {
                    polygonContext += String.format(" GeoJSON: %s. Используй эти геоданные для определения местоположения и районирования культур, если это возможно.", polygon.getGeoJson());
                }
                polygonContext += " Когда тебя спрашивают об общей информации по текущему полигону (например, \"расскажи мне инфу про этот полигон\"), отвечай кратко (2-3 предложения), фокусируясь на названии полигона и его культуре. Сделай ответ интересным.";
            } else if ("DEMO".equalsIgnoreCase(currentUser.getRole())) {
                 polygonContext = "Ты агро-ассистент. Отвечай на вопросы по сельскому хозяйству.";
            }
            // --- Конец логики формирования контекста полигона ---


            // --- Формирование запроса к OpenAI API ---
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", "gpt-3.5-turbo"); // Или другая модель, если используете
            
            com.google.gson.JsonArray messagesArray = new com.google.gson.JsonArray();
            
            // Добавляем системный контекст, если он есть
            if (!polygonContext.isEmpty()) {
                JsonObject systemMessage = new JsonObject();
                systemMessage.addProperty("role", "system");
                systemMessage.addProperty("content", polygonContext);
                messagesArray.add(systemMessage);
            }

            // Добавляем историю чата из фронтенда (уже в правильном формате role/content)
            if (history != null) {
                for (Map<String, String> msg : history) {
                    JsonObject msgObj = new JsonObject();
                    msgObj.addProperty("role", msg.get("role")); // Используем "role"
                    msgObj.addProperty("content", msg.get("content")); // Используем "content"
                    messagesArray.add(msgObj);
                }
            }
            // *** Важно: Последнее сообщение пользователя уже добавлено на фронтенде в history ***
            // Если вы не хотите добавлять его дважды, убедитесь, что history уже содержит userMessage
            // или добавьте его здесь, если history его не содержит.
            // На фронтенде messagesForOpenAI.push({ role: 'user', content: textToSend }); уже добавляет его.
            // Поэтому здесь дополнительных добавлений не требуется.

            requestBody.add("messages", messagesArray);
            requestBody.addProperty("max_tokens", 150); // Пример: ограничение длины ответа

            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    requestBody.toString(), MediaType.parse("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                    .url(openaiApiUrl)
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "No error body";
                    System.err.println("OpenAI API Error: " + response.code() + " - " + errorBody);
                    // Сохраняем ошибку ИИ в чат (если это не демо-пользователь)
                    chatService.saveChatMessage(polygonId.toString(), "ai", "Ошибка при запросе к OpenAI: " + errorBody);

                    return new ResponseEntity<>(
                            Collections.singletonMap("error", "Ошибка при запросе к OpenAI: " + errorBody),
                            HttpStatus.INTERNAL_SERVER_ERROR
                    );
                }

                String responseBody = response.body() != null ? response.body().string() : "{}";
                
                JsonObject openaiResponse = gson.fromJson(responseBody, JsonObject.class);
                String botReply = "Извините, не удалось получить ответ.";

                if (openaiResponse != null && openaiResponse.has("choices")) {
                    com.google.gson.JsonArray choices = openaiResponse.getAsJsonArray("choices");
                    if (choices != null && choices.size() > 0) {
                        JsonObject firstChoice = choices.get(0).getAsJsonObject();
                        if (firstChoice != null && firstChoice.has("message")) {
                            JsonObject message = firstChoice.getAsJsonObject("message");
                            if (message != null && message.has("content")) {
                                botReply = message.get("content").getAsString();
                            }
                        }
                    }
                }
                
                // Сохраняем ответ ИИ через ChatService.
                // ChatService сам решит, сохранять ли в БД (для DEMO - не сохраняет).
                chatService.saveChatMessage(polygonId.toString(), "ai", botReply);

                return new ResponseEntity<>(
                        Collections.singletonMap("reply", botReply),
                        HttpStatus.OK
                    );
                }
            } catch (IOException e) {
                System.err.println("Network/IO Error: " + e.getMessage());
                chatService.saveChatMessage(polygonId.toString(), "ai", "Ошибка сервера: " + e.getMessage());
                return new ResponseEntity<>(
                        Collections.singletonMap("error", "Ошибка сервера: " + e.getMessage()),
                        HttpStatus.INTERNAL_SERVER_ERROR
                );
            } catch (JsonParseException e) {
                System.err.println("JSON Parsing Error: " + e.getMessage());
                chatService.saveChatMessage(polygonId.toString(), "ai", "Ошибка обработки ответа от OpenAI.");
                return new ResponseEntity<>(
                        Collections.singletonMap("error", "Ошибка обработки ответа от OpenAI."),
                        HttpStatus.INTERNAL_SERVER_ERROR
                );
            } catch (Exception e) {
                System.err.println("Unexpected Error: " + e.getMessage());
                chatService.saveChatMessage(polygonId.toString(), "ai", "Произошла непредвиденная ошибка.");
                return new ResponseEntity<>(
                        Collections.singletonMap("error", "Произошла непредвиденная ошибка."),
                        HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
        }

    // Удаление истории чата для полигона
    @DeleteMapping("/chat/polygons/{polygonId}/messages")
    @Transactional
    public ResponseEntity<?> clearChatHistory(@PathVariable UUID polygonId,
                                              @AuthenticationPrincipal User principalUser) {
        if (principalUser == null || principalUser.getUsername() == null) {
            return new ResponseEntity<>(
                    Collections.singletonMap("error", "Пользователь не аутентифицирован."),
                    HttpStatus.UNAUTHORIZED
                );
            }

        try {
            // Делегируем удаление истории чата сервисному слою.
            // ChatService сам определяет, нужно ли обращаться к БД для DEMO-пользователей.
            chatService.deleteChatMessagesByPolygonId(polygonId.toString());
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return new ResponseEntity<>(
                    Collections.singletonMap("error", e.getMessage()),
                    HttpStatus.FORBIDDEN
            );
        } catch (RuntimeException e) {
            return new ResponseEntity<>(
                    Collections.singletonMap("error", e.getMessage()),
                    HttpStatus.NOT_FOUND
            );
        } catch (Exception e) {
            return new ResponseEntity<>(
                    Collections.singletonMap("error", "Произошла непредвиденная ошибка при очистке истории чата."),
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
}
