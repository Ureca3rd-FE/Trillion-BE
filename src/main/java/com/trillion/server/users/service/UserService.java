package com.trillion.server.users.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trillion.server.users.domain.UserEntity;
import com.trillion.server.users.domain.UserEntity.UserStatus;
import com.trillion.server.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserEntity getCurrentUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID가 필요합니다.");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("사용자를 찾을 수 없습니다."));
    }

    @Transactional
    public void deleteAccount(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID가 필요합니다.");
        }
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("사용자를 찾을 수 없습니다."));
        
        if (user.getStatus() == UserStatus.DELETED) {
            throw new IllegalArgumentException("이미 탈퇴한 사용자입니다.");
        }
        
        user.updateStatus(UserStatus.DELETED);
        userRepository.save(user);
    }
}
