package org.example.service;

import org.example.dto.QuoteRequest;
import org.example.dto.QuoteResponse;

public interface PricingService {

    // Prices a cart for the caller. Admins may quote against any vehicle, customers only their own.
    QuoteResponse quote(QuoteRequest request, String actingUserEmail, boolean admin);
}
