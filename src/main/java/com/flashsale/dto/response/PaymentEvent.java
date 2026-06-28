package com.flashsale.dto.response;

import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEvent {

    private String paymentId;
    private String orderId;
    private BigDecimal amount;
    private String paymentMethod;
    private String status;       // SUCCESS or FAILED
    private String forceStatus;  // optional override
    private String idempotencyKey;
    private String failureReason;
}
