// src/main/java/com/example/backend/service/SentinelHubAuthService.java
package com.example.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

@Service
@Slf4j
public class SentinelHubAuthService {

    @Value("${sentinelhub.oauth.client-id}")
    private String clientId;

    @Value("${sentinelhub.oauth.client-secret}")
    private String clientSecret;

    @Value("${sentinelhub.oauth.token-url}")
    private String tokenUrl;

    private String accessToken;
    private Instant tokenExpiryTime;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String getAccessToken() {
        // Проверяем, действителен ли текущий токен (с запасом в 60 секунд)
        if (accessToken == null || tokenExpiryTime == null || Instant.now().plusSeconds(60).isAfter(tokenExpiryTime)) {
            log.info("Access token is missing or expired. Fetching a new one from Sentinel Hub.");
            fetchNewAccessToken(); // Вызываем метод для получения нового токена
            log.info("Токен Sentinel Hub обновлен."); // Сообщение об обновлении
        }
        return accessToken;
    }

    /**
     * Выполняет запрос к Sentinel Hub OAuth для получения нового Access Token.
     */
    private void fetchNewAccessToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            String response = restTemplate.postForObject(tokenUrl, request, String.class);
            JsonNode rootNode = objectMapper.readTree(response);

            this.accessToken = rootNode.path("access_token").asText();
            long expiresIn = rootNode.path("expires_in").asLong(); // Срок действия в секундах

            // --- ОРИГИНАЛЬНАЯ ЛОГИКА ДЛЯ PRODUCTION ---
            this.tokenExpiryTime = Instant.now().plusSeconds(expiresIn);
            log.info("Successfully fetched new Access Token. Expires in {} seconds.", expiresIn);

        } catch (HttpClientErrorException e) {
            log.error("HTTP error fetching Sentinel Hub Access Token: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to get Sentinel Hub Access Token: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Error fetching Sentinel Hub Access Token: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get Sentinel Hub Access Token", e);
        }
    }
}
