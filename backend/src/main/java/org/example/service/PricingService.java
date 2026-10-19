package org.example.service;

import org.example.dto.QuoteRequest;
import org.example.dto.QuoteResponse;
import org.example.entity.Accessory;

import java.util.Collection;

public interface PricingService {

    // Prices a cart for the caller. Admins may quote against any vehicle, customers only their own.
    QuoteResponse quote(QuoteRequest request, String actingUserEmail, boolean admin);

    // Order creation already holds the accessory rows for update and supplies their batch-loaded entities.
    QuoteResponse quoteForOrder(QuoteRequest request, String actingUserEmail, boolean admin,
                                Collection<Accessory> accessories);
}
