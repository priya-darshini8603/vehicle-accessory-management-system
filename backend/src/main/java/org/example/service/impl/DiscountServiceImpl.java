package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.DiscountRequest;
import org.example.dto.DiscountResponse;
import org.example.dto.PageResponse;
import org.example.entity.Discount;
import org.example.exception.DuplicateResourceException;
import org.example.exception.ResourceNotFoundException;
import org.example.mapper.DiscountMapper;
import org.example.repository.AccessoryRepository;
import org.example.repository.DiscountRepository;
import org.example.service.DiscountService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscountServiceImpl implements DiscountService {

    private final DiscountRepository discountRepository;
    private final AccessoryRepository accessoryRepository;
    private final DiscountMapper discountMapper;

    @Override
    @Transactional
    public DiscountResponse createDiscount(DiscountRequest request) {
        if (request.code() != null && discountRepository.existsByCode(request.code())) {
            throw duplicateCode(request.code());
        }
        Discount discount = new Discount();
        apply(discount, request);
        Discount saved = discountRepository.save(discount);
        log.info("Created discount with id {}", saved.getId());
        return discountMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DiscountResponse> getDiscounts(Pageable pageable) {
        return PageResponse.from(discountRepository.findAll(pageable), discountMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public DiscountResponse getDiscountById(Long id) {
        return discountMapper.toResponse(findDiscount(id));
    }

    @Override
    @Transactional
    public DiscountResponse updateDiscount(Long id, DiscountRequest request) {
        if (request.version() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "version is required: send the version from your last GET of this discount");
        }
        Discount discount = findDiscount(id);
        // @Version alone only catches saves that overlap in flight. Comparing the client's version also
        // catches an edit based on a stale read (admin A and B both GET, A saves, then B saves).
        if (!request.version().equals(discount.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Discount.class, id);
        }
        if (request.code() != null && discountRepository.existsByCodeAndIdNot(request.code(), id)) {
            throw duplicateCode(request.code());
        }
        apply(discount, request);
        // Flush so @UpdateTimestamp and the @Version check are applied before the response is built.
        discountRepository.flush();
        log.info("Updated discount with id {}", id);
        return discountMapper.toResponse(discount);
    }

    @Override
    @Transactional
    public void deleteDiscount(Long id) {
        discountRepository.delete(findDiscount(id));
        log.info("Deleted discount with id {}", id);
    }

    private void apply(Discount discount, DiscountRequest request) {
        discountMapper.updateEntity(discount, request);
        discount.setAccessory(request.accessoryId() == null ? null
                : accessoryRepository.findById(request.accessoryId())
                        .orElseThrow(() -> ResourceNotFoundException.of("Accessory", request.accessoryId())));
    }

    private Discount findDiscount(Long id) {
        return discountRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Discount", id));
    }

    private static DuplicateResourceException duplicateCode(String code) {
        return new DuplicateResourceException("Discount code '" + code + "' already exists");
    }
}
