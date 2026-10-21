package com.dc.medapp.medappbe.billing;

import com.dc.medapp.medappbe.audit.Audited;
import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

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

}