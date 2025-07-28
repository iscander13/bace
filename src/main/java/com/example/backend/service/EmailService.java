package com.example.backend.service;

import com.example.backend.dto.ContactFormDTO; // Убедитесь, что импорт правильный

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException; // Добавьте этот импорт
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils; // Добавьте этот импорт

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    /**
     * Отправляет код восстановления пароля на указанный email.
     * @param toEmail Email получателя.
     * @param code Код восстановления.
     */
    public void sendResetCode(String toEmail, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Код восстановления пароля");
        message.setText("Ваш код: " + code + "\nОн действителен 15 минут.");
        // Предполагается, что 'from' адрес будет установлен в application.properties
        // или вы можете установить его здесь: message.setFrom("VerifPharmacy@gmail.com");
        mailSender.send(message);
    }

    // --- Новый метод для отправки контактной информации ---

    /**
     * Отправляет контактную информацию (email или номер телефона) на заданный адрес.
     * @param contactForm DTO, содержащее контактную информацию.
     */
    public void sendContactInfoEmail(ContactFormDTO contactForm) {
        // Проверка: поле contact должно быть заполнено
        if (!StringUtils.hasText(contactForm.getContact())) {
            throw new IllegalArgumentException("Контактная информация (email или номер телефона) не может быть пустой.");
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("VerifPharmacy@gmail.com"); // Ваша почта как отправитель
            message.setTo("info@agrofarm.kz"); // Почта получателя
            message.setSubject("Новый контакт с сайта"); // Тема письма

            String emailContent = """
                📬 Новый контакт с сайта https://agrofarm.kz/:

                👤 Контактная информация:
                %s

                📅 Дата отправки: %s

                ———
                Письмо сгенерировано автоматически. Пожалуйста, не отвечайте на него.
                """.formatted(contactForm.getContact(), LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));

                message.setText(emailContent);
                message.setText(emailContent);
            mailSender.send(message);
            System.out.println("Email с контактной информацией отправлен успешно!");
        } catch (MailException e) {
            System.err.println("Ошибка при отправке email с контактной информацией: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Не удалось отправить email с контактной информацией. Пожалуйста, попробуйте еще раз позже.", e);
        }
    }
}
