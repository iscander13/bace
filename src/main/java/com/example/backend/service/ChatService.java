package com.example.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.AI.OpenAIService;
import com.example.backend.entiity.ChatMessage;
import com.example.backend.entiity.PolygonArea;
import com.example.backend.entiity.User;
import com.example.backend.repository.ChatMessageRepository;
import com.example.backend.repository.PolygonAreaRepository;
import com.example.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final PolygonAreaRepository polygonAreaRepository;
    private final UserRepository userRepository;
    private final OpenAIService openAIService;

    private User getCurrentAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("ChatService: Пользователь не аутентифицирован.");
            throw new IllegalStateException("Пользователь не аутентифицирован.");
        }

        if (authentication.getPrincipal() instanceof User) {
            User authenticatedUser = (User) authentication.getPrincipal();
            log.info("ChatService: Principal is custom User object. Email: {}, Role: {}", authenticatedUser.getEmail(), authenticatedUser.getRole());
            return authenticatedUser;
        }

        String userEmail = authentication.getName();
        log.info("ChatService: Principal is not custom User object. Attempting to load user by email: {}", userEmail);
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден в базе данных: " + userEmail));
    }

    @Transactional
    public ChatMessage saveChatMessage(String polygonId, String sender, String messageText, List<ChatMessage> history) {
        User currentUser = getCurrentAuthenticatedUser();
        log.info("ChatService: Saving message for user ID: {}, role: {}", currentUser.getId(), currentUser.getRole());

        if ("DEMO".equalsIgnoreCase(currentUser.getRole())) {
            log.info("ChatService: DEMO user detected. Skipping database save for chat message.");
            ChatMessage demoMessage = ChatMessage.builder()
                    .id(UUID.randomUUID())
                    .sender(sender)
                    .text(messageText)
                    .timestamp(LocalDateTime.now())
                    .user(currentUser)
                    .polygonArea(null)
                    .build();
            return demoMessage;
        }

        PolygonArea polygonArea = polygonAreaRepository.findById(UUID.fromString(polygonId))
                .orElseThrow(() -> new RuntimeException("Полигон не найден с ID: " + polygonId));

        if (!polygonArea.getUser().getId().equals(currentUser.getId())) {
            log.warn("ChatService: User {} attempted to save message to polygon {} not owned by them.", currentUser.getId(), polygonId);
            throw new SecurityException("У вас нет разрешения на сохранение сообщения для этого полигона.");
        }

        ChatMessage chatMessage = ChatMessage.builder()
                .sender(sender)
                .text(messageText)
                .timestamp(LocalDateTime.now())
                .user(currentUser)
                .polygonArea(polygonArea)
                .build();

        return chatMessageRepository.save(chatMessage);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getChatMessagesByPolygonId(String polygonId) {
        User currentUser = getCurrentAuthenticatedUser();
        log.info("ChatService: Fetching messages for user ID: {}, role: {}", currentUser.getId(), currentUser.getRole());

        if ("DEMO".equalsIgnoreCase(currentUser.getRole())) {
            log.info("ChatService: DEMO user detected. Returning empty list for chat history (frontend handles local storage).");
            return List.of();
        }

        PolygonArea polygonArea = polygonAreaRepository.findById(UUID.fromString(polygonId))
                .orElseThrow(() -> new RuntimeException("Полигон не найден с ID: " + polygonId));

        if (!polygonArea.getUser().getId().equals(currentUser.getId())) {
            log.warn("ChatService: User {} attempted to access messages for polygon {} not owned by them.", currentUser.getId(), polygonId);
            throw new SecurityException("У вас нет разрешения на просмотр сообщений для этого полигона.");
        }

        // Эта строка правильна для реальных пользователей
        return chatMessageRepository.findByUser_IdAndPolygonArea_IdOrderByTimestampAsc( // Исправлен порядок аргументов
                currentUser.getId(), UUID.fromString(polygonId)); // Передаем userId первым, затем polygonId
    }

    @Transactional
    public void deleteChatMessagesByPolygonId(String polygonId) {
        User currentUser = getCurrentAuthenticatedUser();
        log.info("ChatService: Deleting messages for user ID: {}, role: {}", currentUser.getId(), currentUser.getRole());

        if ("DEMO".equalsIgnoreCase(currentUser.getRole())) {
            log.info("ChatService: DEMO user detected. Skipping database delete for chat messages.");
            return;
        }

        PolygonArea polygonArea = polygonAreaRepository.findById(UUID.fromString(polygonId))
                .orElseThrow(() -> new RuntimeException("Полигон не найден с ID: " + polygonId));

        if (!polygonArea.getUser().getId().equals(currentUser.getId())) {
            log.warn("ChatService: User {} attempted to delete messages for polygon {} not owned by them.", currentUser.getId(), polygonId);
            throw new SecurityException("У вас нет разрешения на удаление сообщений для этого полигона.");
        }

        chatMessageRepository.deleteByPolygonArea_IdAndUser_Id(UUID.fromString(polygonId), currentUser.getId());
        log.info("ChatService: Deleted chat messages for polygon ID: {} for user ID: {}", polygonId, currentUser.getId());
    }

    public String getAiResponse(String userMessage, List<ChatMessage> history, String polygonId) {
        User currentUser = getCurrentAuthenticatedUser();
        log.info("ChatService: Getting AI response for user ID: {}, role: {}", currentUser.getId(), currentUser.getRole());

        Optional<PolygonArea> polygonOptional = Optional.empty();
        if ("DEMO".equalsIgnoreCase(currentUser.getRole())) {
            log.warn("ChatService: DEMO user, cannot fetch polygon from DB for AI context.");
        } else {
            polygonOptional = polygonAreaRepository.findById(UUID.fromString(polygonId));
        }

        List<com.example.backend.AI.Message> aiHistory = history.stream()
            .map(msg -> new com.example.backend.AI.Message(msg.getSender(), msg.getText()))
            .collect(Collectors.toList());

        String polygonContext = "";
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

        return openAIService.getChatCompletion(userMessage, aiHistory, polygonContext);
    }
}
