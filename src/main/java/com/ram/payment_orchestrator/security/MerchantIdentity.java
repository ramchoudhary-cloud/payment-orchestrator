package com.ram.payment_orchestrator.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;

@Component
public class MerchantIdentity {

    public String requireMerchantId(Jwt jwt) {
        String merchantId = jwt == null ? null : jwt.getClaimAsString("merchant_id");

        if (merchantId == null || merchantId.isBlank()) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "JWT must contain a merchant_id claim"
            );
        }

        return merchantId;
    }
}
