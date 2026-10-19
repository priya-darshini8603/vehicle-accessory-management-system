package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.AccessoryRequest;
import org.example.dto.AccessoryResponse;
import org.example.dto.PageResponse;
import org.example.dto.PriceChangeResponse;
import org.example.entity.Accessory;
import org.example.entity.PriceChange;
import org.example.exception.ResourceNotFoundException;
import org.example.mapper.AccessoryMapper;
import org.example.repository.AccessoryRepository;
import org.example.repository.DiscountRepository;
import org.example.repository.PriceChangeRepository;
import org.example.service.AccessoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccessoryServiceImpl implements AccessoryService {

    // Saves and reads accessory records.
    private final AccessoryRepository accessoryRepository;
    private final AccessoryMapper accessoryMapper;
    private final DiscountRepository discountRepository;
    private final PriceChangeRepository priceChangeRepository;

    @Override
    @Transactional
    public AccessoryResponse createAccessory(AccessoryRequest request, String actingUserEmail) {
        Accessory savedAccessory = accessoryRepository.save(accessoryMapper.toEntity(request));
        recordPriceChange(savedAccessory.getId(), null, null, savedAccessory, actingUserEmail);
        log.info("Created accessory with id {}", savedAccessory.getId());
        return accessoryMapper.toResponse(savedAccessory);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AccessoryResponse> getAccessories(Pageable pageable) {
        Page<Accessory> page = accessoryRepository.findAll(pageable);
        log.debug("Retrieved {} of {} accessories", page.getNumberOfElements(), page.getTotalElements());
        return PageResponse.from(page, accessoryMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AccessoryResponse getAccessoryById(Long id) {
        Accessory accessory = findAccessoryById(id);
        log.debug("Retrieved accessory with id {}", id);
        return accessoryMapper.toResponse(accessory);
    }

    @Override
    @Transactional
    public AccessoryResponse updateAccessory(Long id, AccessoryRequest request, String actingUserEmail) {
        Accessory existing = findAccessoryById(id);
        BigDecimal oldPrice = existing.getPrice();
        BigDecimal oldCostPrice = existing.getCostPrice();
        accessoryMapper.updateEntity(existing, request);
        if (!sameAmount(oldPrice, existing.getPrice()) || !sameAmount(oldCostPrice, existing.getCostPrice())) {
            recordPriceChange(id, oldPrice, oldCostPrice, existing, actingUserEmail);
            log.info("Price of accessory {} changed from {} to {} by {}", id, oldPrice, existing.getPrice(),
                    actingUserEmail);
        }
        // Flush so @UpdateTimestamp is applied before the response is built.
        accessoryRepository.flush();
        log.info("Updated accessory with id {}", id);
        return accessoryMapper.toResponse(existing);
    }

    /**
     * Deletes the accessory after removing it from every vehicle it is mapped to.
     */
    @Override
    @Transactional
    public void deleteAccessory(Long id) {
        if (!accessoryRepository.existsById(id)) {
            log.warn("Accessory with the id {} was not found", id);
            throw ResourceNotFoundException.of("Accessory", id);
        }
        // Discounts aimed only at this accessory would otherwise block the delete (foreign key).
        // The price history is kept on purpose: it has no foreign key.
        discountRepository.deleteByAccessoryId(id);
        accessoryRepository.deleteVehicleMappings(id);
        accessoryRepository.deleteById(id);
        log.info("Deleted accessory with id {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PriceChangeResponse> getPriceHistory(Long accessoryId, Pageable pageable) {
        // History outlives the accessory, so a deleted accessory's history is still readable.
        Page<PriceChange> page = priceChangeRepository.findByAccessoryIdOrderByChangedAtDescIdDesc(accessoryId, pageable);
        if (page.isEmpty() && page.getNumber() == 0 && !accessoryRepository.existsById(accessoryId)) {
            throw ResourceNotFoundException.of("Accessory", accessoryId);
        }
        return PageResponse.from(page, change -> new PriceChangeResponse(
                change.getId(),
                change.getAccessoryId(),
                change.getOldPrice(),
                change.getNewPrice(),
                change.getOldCostPrice(),
                change.getNewCostPrice(),
                change.getChangedBy(),
                change.getChangedAt()));
    }

    private void recordPriceChange(Long accessoryId, BigDecimal oldPrice, BigDecimal oldCostPrice,
                                   Accessory updated, String actingUserEmail) {
        PriceChange change = new PriceChange();
        change.setAccessoryId(accessoryId);
        change.setOldPrice(oldPrice);
        change.setNewPrice(updated.getPrice());
        change.setOldCostPrice(oldCostPrice);
        change.setNewCostPrice(updated.getCostPrice());
        change.setChangedBy(actingUserEmail);
        priceChangeRepository.save(change);
    }

    // 1499.0 and 1499.00 are the same price; BigDecimal.equals would say otherwise.
    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        return Objects.equals(a, b) || (a != null && b != null && a.compareTo(b) == 0);
    }

    // Finds an accessory and throws a clear error if missing.
    private Accessory findAccessoryById(Long id) {
        return accessoryRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Accessory with id {} was not found", id);
                    return ResourceNotFoundException.of("Accessory", id);
                });
    }
}
