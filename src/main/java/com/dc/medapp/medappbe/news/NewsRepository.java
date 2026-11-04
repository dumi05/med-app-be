package com.dc.medapp.medappbe.news;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface NewsRepository extends JpaRepository<NewsEntity, Long> {
    List<NewsEntity> findByExpiresAtGreaterThanEqualOrderByCreatedAtDescIdDesc(LocalDate today);
    List<NewsEntity> findByAuthorIdOrderByCreatedAtDescIdDesc(Long id);
}
