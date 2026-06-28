package com.flashsale.service;

import com.flashsale.dto.request.ProductRequest;
import com.flashsale.dto.response.ProductResponse;
import com.flashsale.exception.ResourceNotFoundException;
import com.flashsale.model.entity.Product;
import com.flashsale.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        log.info("Creating product: {}", request.getName());

        Product product = Product.builder()
                .name(request.getName())
                .price(request.getPrice())
                .totalStock(request.getStock())
                .availableStock(request.getStock())   // initially all stock is available
                .reservedStock(0)
                .saleStartTime(request.getSaleStartTime())
                .saleEndTime(request.getSaleEndTime())
                .build();

        Product saved = productRepository.save(product);
        log.info("Product created with id: {}", saved.getId());
        return ProductResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        return ProductResponse.from(product);
    }
}
