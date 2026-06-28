package com.flashsale.dto.response;

import com.flashsale.model.entity.Product;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {

    private Long id;
    private String name;
    private BigDecimal price;
    private Integer totalStock;
    private Integer availableStock;
    private Integer reservedStock;
    private LocalDateTime saleStartTime;
    private LocalDateTime saleEndTime;
    private boolean saleActive;
    private LocalDateTime createdAt;

    public static ProductResponse from(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getPrice())
                .totalStock(product.getTotalStock())
                .availableStock(product.getAvailableStock())
                .reservedStock(product.getReservedStock())
                .saleStartTime(product.getSaleStartTime())
                .saleEndTime(product.getSaleEndTime())
                .saleActive(product.isSaleActive())
                .createdAt(product.getCreatedAt())
                .build();
    }
}
