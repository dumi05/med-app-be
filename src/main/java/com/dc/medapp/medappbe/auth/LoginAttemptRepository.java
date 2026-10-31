package com.dc.medapp.medappbe.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttemptEntity, Long> {
    Optional<LoginAttemptEntity> findByEmail(String email);
    void deleteByEmail(String email);
}
