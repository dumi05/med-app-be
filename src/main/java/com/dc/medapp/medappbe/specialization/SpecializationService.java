package com.dc.medapp.medappbe.specialization;

import com.dc.medapp.medappbe.audit.Audited;
import com.dc.medapp.medappbe.billing.BillingService;
import com.dc.medapp.medappbe.billing.PriceListEntity;
import com.dc.medapp.medappbe.billing.PriceListRepository;
import com.dc.medapp.medappbe.common.exception.SpecializationAlreadyExistsException;
import com.dc.medapp.medappbe.specialization.dto.CreateSpecializationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SpecializationService {

    private static final int MIN_NAME_LENGTH = 2;

    private final SpecializationRepository specializationRepository;
    private final PriceListRepository priceListRepository;

    @Transactional(readOnly = true)
    public List<SpecializationEntity> getAll() {
        return specializationRepository.findAll();
    }

    @Transactional
    @Audited(action = "CREATE_SPECIALIZATION", entity = "Specialization")
    public SpecializationEntity create(CreateSpecializationRequest request) {
        var name = request.name().trim();
        if (name.length() < MIN_NAME_LENGTH) {
            throw new IllegalArgumentException("Il nome della specializzazione deve avere almeno " + MIN_NAME_LENGTH + " caratteri.");
        }
        if (specializationRepository.existsByNameIgnoreCase(name)) {
            throw new SpecializationAlreadyExistsException(name);
        }

        SpecializationEntity specialization = specializationRepository.save(SpecializationEntity.builder().name(name).build());

        priceListRepository.save(PriceListEntity.builder()
                .specialization(specialization)
                .visitType(BillingService.STANDARD_VISIT_TYPE)
                .price(request.price())
                .build());
        return specialization;
    }
}
