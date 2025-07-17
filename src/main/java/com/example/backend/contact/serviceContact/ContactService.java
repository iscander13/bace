package com.example.backend.contact.serviceContact;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service // Отмечаем этот класс как Spring-сервис
public class ContactService {

    @Autowired
    private JavaMailSender mailSender; // Инжектируем MailSender

    public void sendContactEmail(String senderInfo) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("VerifPharmacy@gmail.com"); // Твоя почта-отправитель из конфига
        message.setTo("info@agrofarm.kz"); // Куда отправлять данные
        message.setSubject("Новая заявка с Agrofarm.kz");
        message.setText("Пользователь оставил контактные данные: " + senderInfo);

        mailSender.send(message);
    }
}
