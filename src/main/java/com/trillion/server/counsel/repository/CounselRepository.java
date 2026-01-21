package com.trillion.server.counsel.repository;

import com.trillion.server.counsel.entity.CounselEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CounselRepository extends JpaRepository<CounselEntity, Long> {
    List<CounselEntity> findAllByUserIdOrderByCounselDateDesc(Long userId);
}