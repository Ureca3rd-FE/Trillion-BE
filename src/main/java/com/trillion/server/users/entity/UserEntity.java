package com.trillion.server.users.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_kakao_id", columnList = "kakao_id"),
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kakao_id", nullable = false, unique = true, length = 255)
    private String kakaoId;

    @Column(name = "nickname", nullable = false, length = 100)
    private String nickname;

    @Column(name = "refresh_token", length = 500)
    private String refreshToken;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role;

    @Builder
    public UserEntity(String kakaoId, String nickname, Role role) {
        this.kakaoId = kakaoId;
        this.nickname = nickname;
        this.role = role;
    }

    public void upgradToUser(){
        this.role = Role.USER;
    }

    public void updateRefreshToken(String refreshToken){
        this.refreshToken = refreshToken;
    }
}
