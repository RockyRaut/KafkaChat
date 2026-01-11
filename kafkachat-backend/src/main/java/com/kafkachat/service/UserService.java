package com.kafkachat.service;

import com.kafkachat.dto.UserDTO;
import com.kafkachat.entity.User;
import com.kafkachat.repository.UserRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User registerUser(UserDTO request) {
        String rawPassword = request.getPassword() == null || request.getPassword().isBlank()
                ? "change-me"
                : request.getPassword();

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(rawPassword))
                .profileImage(request.getProfileImage())
                .status(request.getStatus())
                .build();
        return userRepository.save(user);
    }

    public User validateUser(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        return user;
    }

    public List<UserDTO> listUsers() {
        return userRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    public UserDTO getUser(Long id) {
        return userRepository.findById(id).map(this::toDto)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
    }

    private UserDTO toDto(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .profileImage(user.getProfileImage())
                .status(user.getStatus())
                .online(user.isOnline())
                .build();
    }
}

