package com.trillion.server.counsel.repository;

import com.trillion.server.counsel.entity.CounselEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CounselRepository extends JpaRepository<CounselEntity, Long> {

    List<CounselEntity> findAllByUserIdOrderByCounselDateDesc(Long userId);

    List<CounselEntity> findAllByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    @Query("SELECT c FROM CounselEntity c WHERE c.user.id = :userId AND c.id < :cursorId ORDER BY c.id DESC")
    List<CounselEntity> findByUserIdAndIdLessThan(@Param("userId") Long userId, @Param("cursorId") Long cursorId, Pageable pageable);
}