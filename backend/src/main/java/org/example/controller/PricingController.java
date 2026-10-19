package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.QuoteRequest;
import org.example.dto.QuoteResponse;
import org.example.service.PricingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pricing")
@PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER')")
@RequiredArgsConstructor
public class PricingController {

    private final PricingService pricingService;

    // POST rather than GET: the cart is a structured body. Nothing is saved, so it is safe to repeat.
    @PostMapping("/quote")
    public ResponseEntity<QuoteResponse> quote(
            @Valid @RequestBody QuoteRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {
        boolean admin = currentUser.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return ResponseEntity.ok(pricingService.quote(request, currentUser.getUsername(), admin));
    }
}
