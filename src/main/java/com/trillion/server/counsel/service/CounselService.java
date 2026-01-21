package com.trillion.server.counsel.service;

import com.trillion.server.common.exception.ErrorMessages;
import com.trillion.server.counsel.dto.CounselDto;
import com.trillion.server.counsel.entity.CounselEntity;
import com.trillion.server.counsel.repository.CounselRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CounselService {
    private final CounselRepository counselRepository;

    public List<CounselDto.CounselListResponse> getCounselList(Long userId){
        List<CounselEntity> counsels = counselRepository.findAllByUserIdOrderByCounselDateDesc(userId);

        return counsels.stream()
                .map(CounselDto.CounselListResponse::from)
                .collect(Collectors.toList());
    }

    public CounselDto.CounselDetailResponse getCounselDetail(Long userId, Long counselId) {
        CounselEntity counsel = counselRepository.findById(counselId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorMessages.COUNSEL_NOT_FOUND));

        if (!counsel.getUser().getId().equals(userId)) {
            throw new AccessDeniedException(ErrorMessages.FORBIDDEN);
        }
        return CounselDto.CounselDetailResponse.from(counsel);
    }
}
