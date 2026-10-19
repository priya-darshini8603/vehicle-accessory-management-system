package org.example.service;

import org.example.dto.DiscountRequest;
import org.example.dto.DiscountResponse;
import org.example.dto.PageResponse;
import org.springframework.data.domain.Pageable;

// Admin management of promotions and coupons.
public interface DiscountService {

    DiscountResponse createDiscount(DiscountRequest request);

    PageResponse<DiscountResponse> getDiscounts(Pageable pageable);

    DiscountResponse getDiscountById(Long id);

    DiscountResponse updateDiscount(Long id, DiscountRequest request);

    void deleteDiscount(Long id);
}
