// service/AuthService.java
package org.example.service;

import org.example.model.User;
import org.example.repository.UserRepository;
import org.example.utils.TokenGenerator;

public class AuthService {
    private UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User registerUser(String username, String department, String role) {
        String token = TokenGenerator.generateToken();
        User user = new User(username, department, role, token);
        userRepository.addUser(user);
        return user;
    }

    public User login(String token) {
        return userRepository.getUserByToken(token);
    }
}
