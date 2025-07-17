package com.example.backend.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.backend.entiity.User;
import com.example.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    log.info("UserDetailsServiceImpl: Loading user by email: {}", email);

    User user = userRepository.findByEmail(email)
            .orElseThrow(() -> {
                log.warn("UserDetailsServiceImpl: User not found with email: {}", email);
                return new UsernameNotFoundException("User not found with email: " + email);
            });

    // Добавьте этот лог
    if (user != null) {
        log.info("UserDetailsServiceImpl: Found user {} with ID: {}", user.getEmail(), user.getId());
    }

    return user;
}
}
