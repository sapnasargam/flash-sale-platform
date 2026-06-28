package com.flashsale.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRequest {

    @NotBlank(message = "Product name is required")
    private String name;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    private BigDecimal price;

    @NotNull(message = "Stock is required")
    @Min(value = 1, message = "Stock must be at least 1")
    private Integer stock;

    @NotNull(message = "Sale start time is required")
    private LocalDateTime saleStartTime;

    @NotNull(message = "Sale end time is required")
    private LocalDateTime saleEndTime;
}
