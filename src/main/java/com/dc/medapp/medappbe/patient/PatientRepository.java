package com.dc.medapp.medappbe.patient;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatientRepository extends JpaRepository<PatientEntity, Long> {
    Optional<PatientEntity> findByUserId(Long userId);

    @Query("""
            select p from PatientEntity p
            where p.anonymized = false
              and (lower(p.firstName) like lower(concat('%', :text, '%')) escape '\\'
                or lower(p.lastName) like lower(concat('%', :text, '%')) escape '\\'
                or lower(p.user.email) like lower(concat('%', :text, '%')) escape '\\')
            order by p.lastName, p.firstName, p.id
            """)
    List<PatientEntity> searchByText(@Param("text") String text, Pageable pageable);
}
