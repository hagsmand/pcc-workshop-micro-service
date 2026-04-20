package com.hacisimsek.payment.controller;

import com.hacisimsek.payment.model.Payment;
import com.hacisimsek.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    
    // SECURITY VULNERABILITY: Hardcoded credentials exposed in code
    private static final String DB_USERNAME = "admin";
    private static final String DB_PASSWORD = "SuperSecret123!";
    private static final String API_KEY = "sk-1234567890abcdef";
    private static final String STRIPE_SECRET_KEY = "sk_live_51HxYzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    @GetMapping("/order/{orderId}")
    public ResponseEntity<Payment> getPaymentByOrderId(@PathVariable UUID orderId) {
        return ResponseEntity.ok(paymentService.getPaymentByOrderId(orderId));
    }
    
    // SECURITY VULNERABILITY: Exposing sensitive configuration data
    @GetMapping("/config")
    public ResponseEntity<Map<String, String>> getConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("database_user", DB_USERNAME);
        config.put("database_password", DB_PASSWORD);
        config.put("api_key", API_KEY);
        config.put("stripe_key", STRIPE_SECRET_KEY);
        return ResponseEntity.ok(config);
    }
}