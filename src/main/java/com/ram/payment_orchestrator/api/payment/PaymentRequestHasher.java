package com.ram.payment_orchestrator.api.payment;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class PaymentRequestHasher {

    public String hash(CreatePaymentRequest request) {
        String canonical = field(request.merchantRef())
                + field(request.amountMinor().toString())
                + field(request.currency())
                + field(request.paymentToken());

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String field(String value) {
        return value.length() + ":" + value;
    }
}
