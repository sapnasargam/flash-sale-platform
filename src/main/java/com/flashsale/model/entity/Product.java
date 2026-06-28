package com.flashsale.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal price;

    // Total original stock
    @Column(nullable = false)
    private Integer totalStock;

    // Available stock (decreases on reservation)
    @Column(nullable = false)
    private Integer availableStock;

    // Reserved stock (increases on reservation, decreases on confirm/release)
    @Column(nullable = false)
    private Integer reservedStock;

    @Column(nullable = false)
    private LocalDateTime saleStartTime;

    @Column(nullable = false)
    private LocalDateTime saleEndTime;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public boolean isSaleActive() {
        LocalDateTime now = LocalDateTime.now();
        return now.isAfter(saleStartTime) && now.isBefore(saleEndTime);
    }
}
