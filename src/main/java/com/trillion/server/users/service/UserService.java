package com.trillion.server.users.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trillion.server.common.exception.ErrorMessages;
import com.trillion.server.users.entity.UserEntity;
import com.trillion.server.users.entity.UserEntity.UserStatus;
import com.trillion.server.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserEntity getCurrentUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException(ErrorMessages.USER_ID_REQUIRED);
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(ErrorMessages.USER_NOT_FOUND));
    }

    @Transactional
    public void deleteAccount(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException(ErrorMessages.USER_ID_REQUIRED);
        }
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(ErrorMessages.USER_NOT_FOUND));
        
        if (user.getStatus() == UserStatus.DELETED) {
            throw new IllegalArgumentException(ErrorMessages.USER_ALREADY_DELETED);
        }
        
        user.updateStatus(UserStatus.DELETED);
        userRepository.save(user);
    }
}
