package com.hacisimsek.order.controller;

import com.hacisimsek.order.dto.OrderRequest;
import com.hacisimsek.order.dto.OrderResponse;
import com.hacisimsek.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final JdbcTemplate jdbcTemplate;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@Valid @RequestBody OrderRequest orderRequest) {
        return orderService.createOrder(orderRequest);
    }

    @GetMapping("/{orderId}")
    public OrderResponse getOrderById(@PathVariable UUID orderId) {
        return orderService.getOrderById(orderId);
    }

    @GetMapping
    public List<OrderResponse> getAllOrders() {
        return orderService.getAllOrders();
    }

    @GetMapping("/customer/{customerId}")
    public List<OrderResponse> getOrdersByCustomerId(@PathVariable UUID customerId) {
        return orderService.getOrdersByCustomerId(customerId);
    }

    // SECURITY VULNERABILITY: SQL Injection - Direct string concatenation in SQL query
    @GetMapping("/search")
    public List<Map<String, Object>> searchOrders(@RequestParam String customerName) {
        // Vulnerable to SQL injection - user input directly concatenated into SQL
        String sql = "SELECT * FROM orders WHERE customer_name = '" + customerName + "'";
        return jdbcTemplate.queryForList(sql);
    }
}