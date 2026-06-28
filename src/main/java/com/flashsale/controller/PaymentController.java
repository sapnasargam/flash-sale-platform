package com.flashsale.controller;

import com.flashsale.dto.request.PaymentRequest;
import com.flashsale.dto.response.ApiResponse;
import com.flashsale.dto.response.PaymentResponse;
import com.flashsale.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "Payment processing APIs")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(summary = "Initiate payment for an order (async via Kafka)")
    public ResponseEntity<ApiResponse<PaymentResponse>> initiatePayment(
            @Valid @RequestBody PaymentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        PaymentResponse response = paymentService.initiatePayment(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(response,
                        "Payment initiated. Processing asynchronously via Kafka."));
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Get payment status by payment ID")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(@PathVariable String paymentId) {
        PaymentResponse response = paymentService.getPayment(paymentId);
        return ResponseEntity.ok(ApiResponse.success(response, "Payment fetched successfully"));
    }
}
