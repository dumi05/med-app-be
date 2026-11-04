package com.dc.medapp.medappbe.news;

import com.dc.medapp.medappbe.common.AuditableEntity;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.leaves.DoctorLeaveEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Table(name = "news")
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NewsEntity extends AuditableEntity {
    @ManyToOne(optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private DoctorEntity author;

    @Column(name = "title", nullable = false, length=120)
    private String title;

    @Column(name = "body", nullable = false, length = 2000)
    private String body;

    @Column(name = "expires_at", nullable = false)
    private LocalDate expiresAt;

    @Builder.Default
    @Column(name = "automatic", nullable = false, columnDefinition = "boolean default false")
    private boolean automatic = false;

    @OneToOne
    @JoinColumn(name = "leave_id", unique = true)
    private DoctorLeaveEntity leave;
}
