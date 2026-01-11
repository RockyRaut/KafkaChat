package com.kafkachat.controller;

import com.kafkachat.dto.AuthResponse;
import com.kafkachat.dto.UserDTO;
import com.kafkachat.entity.User;
import com.kafkachat.service.UserService;
import com.kafkachat.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody UserDTO request) {
        User user = userService.registerUser(request);
        String token = jwtUtil.generateToken(user.getId().toString());
        return ResponseEntity.ok(AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .userId(user.getId())
                .message("Registration successful")
                .build());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody Map<String, String> credentials) {
        String email = credentials.getOrDefault("email", "");
        String password = credentials.getOrDefault("password", "");
        User user = userService.validateUser(email, password);
        String token = jwtUtil.generateToken(user.getId().toString());
        return ResponseEntity.ok(AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .userId(user.getId())
                .message("Login successful")
                .build());
    }
}

