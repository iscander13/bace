package com.example.backend.dto;

public class ContactFormDTO {
    private String contact; // Это поле будет содержать либо email, либо номер телефона

    // Getters and Setters
    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }
}
