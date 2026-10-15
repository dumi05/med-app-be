package com.dc.medapp.medappbe.billing;

import com.dc.medapp.medappbe.common.BaseEntity;
import com.dc.medapp.medappbe.specialization.SpecializationEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "price_list", uniqueConstraints = @UniqueConstraint(columnNames = {"specialization_id", "visit_type"}))
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PriceListEntity extends BaseEntity {
    @ManyToOne(optional = false)
    @JoinColumn(name = "specialization_id", nullable = false, unique = true)
    private SpecializationEntity specialization;

    @Column(name = "visit_type", nullable = false)
    private String visitType;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
}
