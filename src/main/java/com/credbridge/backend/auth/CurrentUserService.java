package com.credbridge.backend.auth;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User requireUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user was not found"));
    }

    public boolean isStaff(User user) {
        return user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.LENDER;
    }
}
