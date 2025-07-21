package com.example.backend.controller;

import com.example.backend.dto.ContactFormDTO;
import com.example.backend.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contact")
public class ContactController {

    @Autowired
    private EmailService emailService;

    @PostMapping("/send")
    public ResponseEntity<String> sendContactForm(@RequestBody ContactFormDTO contactFormDTO) {
        try {
            emailService.sendContactInfoEmail(contactFormDTO); // ИСПРАВЛЕНО: изменено на sendContactInfoEmail
            return new ResponseEntity<>("Сообщение успешно отправлено!", HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            // Ошибка, если не указаны ни email, ни номер телефона
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (RuntimeException e) {
            // Другие ошибки при отправке
            return new ResponseEntity<>("Ошибка при отправке сообщения: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
