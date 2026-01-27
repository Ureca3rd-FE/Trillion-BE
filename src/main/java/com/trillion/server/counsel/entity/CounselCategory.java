package com.trillion.server.counsel.entity;

import com.trillion.server.common.exception.ErrorMessages;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor //
public enum CounselCategory {
    CONSULTATION("상담"),
    ROAMING("로밍"),
    BILLING("요금 및 납부"),
    SERVICE("서비스");

    private final String description;

    public static CounselCategory from(String value){
        return Arrays.stream(values())
                .filter(c -> c.name().equalsIgnoreCase(value) || c.getDescription().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.CATRGORY_NOT_FOUND + value));
    }
}
