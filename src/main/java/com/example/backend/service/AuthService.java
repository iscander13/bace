package com.example.backend.service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.backend.JWT.JwtService;
import com.example.backend.dto.AdminLoginRequest;
import com.example.backend.dto.AdminLoginResponse;
import com.example.backend.dto.DemoLoginRequest;
import com.example.backend.dto.LoginRequest;
import com.example.backend.dto.LoginResponse;
import com.example.backend.dto.RegisterRequest;
import com.example.backend.entiity.User;
import com.example.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public String register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Пользователь с таким email уже существует!");
        }

        User newUser = User.builder()
                .email(request.getEmail()) 
                .passwordHash(passwordEncoder.encode(request.getPassword())) 
                .role("USER") 
                .build();
        userRepository.save(newUser);
        return "Пользователь успешно зарегистрирован!";
    }

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        
        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String jwtToken = jwtService.generateToken(userDetails);

        List<String> roles = userDetails.getAuthorities().stream()
                                    .map(GrantedAuthority::getAuthority)
                                    .collect(Collectors.toList());

        return LoginResponse.builder()
                .message("Вход успешно выполнен!")
                .token(jwtToken)
                .roles(roles)
                .build();
    }

    public LoginResponse demoLogin(DemoLoginRequest request) {
        if ("TEST".equals(request.getUsername()) && "TEST".equals(request.getPassword())) {
            List<String> demoRoles = Collections.singletonList("ROLE_DEMO");
            String demoToken = jwtService.generateDemoToken("TEST", demoRoles); 
            
            return LoginResponse.builder()
                    .message("Демо-доступ к платформе AgroFarm предоставлен!")
                    .token(demoToken)
                    .roles(demoRoles)
                    .build();
        }
        throw new RuntimeException("Неверные учетные данные для демо-доступа. Используйте Логин: TEST, Пароль: TEST");
    }

    public AdminLoginResponse adminLogin(AdminLoginRequest request) {
        // ИСПРАВЛЕНО: Используем request.getUsername(), так как AdminLoginRequest использует 'username'
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()) 
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String jwtToken = jwtService.generateToken(userDetails);

        List<String> roles = userDetails.getAuthorities().stream()
                                    .map(GrantedAuthority::getAuthority)
                                    .collect(Collectors.toList());

        if (!roles.contains("ROLE_ADMIN") && !roles.contains("ROLE_SUPER_ADMIN")) {
            throw new RuntimeException("Доступ запрещен. Пользователь не является администратором.");
        }

        return AdminLoginResponse.builder()
                .message("Вход администратора успешно выполнен!")
                .token(jwtToken)
                .roles(roles)
                .build();
    }
}
