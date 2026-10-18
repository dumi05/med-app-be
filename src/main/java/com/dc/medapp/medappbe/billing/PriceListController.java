package com.dc.medapp.medappbe.billing;

import com.dc.medapp.medappbe.billing.dto.PriceListResponse;
import com.dc.medapp.medappbe.billing.dto.UpdatePriceRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/price-list")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class PriceListController {

    private final PriceListService priceListService;

    @GetMapping
    public ResponseEntity<List<PriceListResponse>> getAll(){
        return ResponseEntity.ok(
                priceListService.findAll().stream().map(PriceListResponse::fromEntity).toList()
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<PriceListResponse> updatePrice(@PathVariable Long id, @Valid @RequestBody UpdatePriceRequest req){
        return ResponseEntity.ok(PriceListResponse.fromEntity(priceListService.updatePrice(id, req.price())));
    }
}
