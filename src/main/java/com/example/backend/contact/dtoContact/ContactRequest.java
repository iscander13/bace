package com.example.backend.contact.dtoContact;

public class ContactRequest {
    private String contactInfo; // Название поля должно совпадать с тем, что отправляет фронтенд (JSON-ключ)

    // Геттеры и сеттеры
    public String getContactInfo() {
        return contactInfo;
    }

    public void setContactInfo(String contactInfo) {
        this.contactInfo = contactInfo;
    }
}
