package com.dc.medapp.medappbe.billing;

import com.dc.medapp.medappbe.audit.Audited;
import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PriceListService {

    private final PriceListRepository priceListRepository;

    @Transactional(readOnly = true)
    public List<PriceListEntity> findAll(){
        return priceListRepository.findAllByOrderBySpecializationNameAscVisitTypeAsc();
    }

    @Transactional
    @Audited(action = "UPDATE_PRICE", entity = "PriceList", idArg = 0)
    public PriceListEntity updatePrice(Long priceListId, BigDecimal newPrice) {
        var entry = priceListRepository.findById(priceListId)
                .orElseThrow(() -> new ResourceNotFoundException("Voce di listino non trovata: " + priceListId));
        entry.setPrice(newPrice);
        return entry;
    }

    @Transactional(readOnly = true)
    public Optional<BigDecimal> standardPriceOf(Long specId) {
        return priceListRepository.findBySpecializationIdAndVisitType(specId, BillingService.STANDARD_VISIT_TYPE).map(PriceListEntity::getPrice);
    }


}