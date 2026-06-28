package com.flashsale;

import com.flashsale.dto.request.OrderRequest;
import com.flashsale.dto.response.OrderResponse;
import com.flashsale.exception.*;
import com.flashsale.model.entity.Order;
import com.flashsale.model.entity.Product;
import com.flashsale.model.enums.OrderStatus;
import com.flashsale.repository.OrderRepository;
import com.flashsale.repository.ProductRepository;
import com.flashsale.service.IdempotencyService;
import com.flashsale.service.OrderService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private IdempotencyService idempotencyService;

    @InjectMocks private OrderService orderService;

    private Product activeProduct;
    private OrderRequest validRequest;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderService, "reservationExpiryMinutes", 2);

        activeProduct = Product.builder()
                .id(1L)
                .name("iPhone 17")
                .price(new BigDecimal("100000"))
                .totalStock(10)
                .availableStock(10)
                .reservedStock(0)
                .saleStartTime(LocalDateTime.now().minusHours(1))
                .saleEndTime(LocalDateTime.now().plusHours(1))
                .build();

        validRequest = OrderRequest.builder()
                .userId(101L)
                .productId(1L)
                .quantity(2)
                .build();
    }

    @Test
    @DisplayName("Should create order successfully when all conditions are met")
    void testCreateOrder_success() {
        // Arrange
        when(idempotencyService.generateHash(anyString())).thenReturn("hash123");
        when(idempotencyService.isReplay(anyString(), anyString())).thenReturn(false);
        when(productRepository.findByIdWithLock(1L)).thenReturn(Optional.of(activeProduct));
        when(orderRepository.existsByUserIdAndProductIdAndStatusNot(101L, 1L, OrderStatus.FAILED))
                .thenReturn(false);
        when(productRepository.reserveInventory(1L, 2)).thenReturn(1);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        OrderResponse response = orderService.createOrder(validRequest, "KEY-001");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getTotalAmount()).isEqualTo(new BigDecimal("200000"));
        verify(productRepository).reserveInventory(1L, 2);
    }

    @Test
    @DisplayName("Should return existing order on idempotency replay")
    void testCreateOrder_idempotencyReplay() {
        // Arrange
        String idempotencyKey = "KEY-REPEAT";
        when(idempotencyService.generateHash(anyString())).thenReturn("hash123");
        when(idempotencyService.isReplay(idempotencyKey, "hash123")).thenReturn(true);

        Order existingOrder = Order.builder()
                .orderId("ORD-EXISTING")
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("200000"))
                .build();
        when(orderRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(existingOrder));

        // Act
        OrderResponse response = orderService.createOrder(validRequest, idempotencyKey);

        // Assert
        assertThat(response.getOrderId()).isEqualTo("ORD-EXISTING");
        // Product lock should NOT be called — we returned early
        verify(productRepository, never()).findByIdWithLock(anyLong());
    }

    @Test
    @DisplayName("Should throw SaleNotActiveException when sale window is over")
    void testCreateOrder_saleNotActive() {
        // Product with expired sale
        activeProduct.setSaleEndTime(LocalDateTime.now().minusHours(1));

        when(idempotencyService.generateHash(anyString())).thenReturn("hash");
        when(idempotencyService.isReplay(anyString(), anyString())).thenReturn(false);
        when(productRepository.findByIdWithLock(1L)).thenReturn(Optional.of(activeProduct));

        assertThatThrownBy(() -> orderService.createOrder(validRequest, "KEY-003"))
                .isInstanceOf(SaleNotActiveException.class);
    }

    @Test
    @DisplayName("Should throw DuplicateOrderException when user already bought the product")
    void testCreateOrder_duplicateOrder() {
        when(idempotencyService.generateHash(anyString())).thenReturn("hash");
        when(idempotencyService.isReplay(anyString(), anyString())).thenReturn(false);
        when(productRepository.findByIdWithLock(1L)).thenReturn(Optional.of(activeProduct));
        when(orderRepository.existsByUserIdAndProductIdAndStatusNot(101L, 1L, OrderStatus.FAILED))
                .thenReturn(true);

        assertThatThrownBy(() -> orderService.createOrder(validRequest, "KEY-004"))
                .isInstanceOf(DuplicateOrderException.class);
    }

    @Test
    @DisplayName("Should throw InsufficientInventoryException when stock runs out")
    void testCreateOrder_insufficientInventory() {
        activeProduct.setAvailableStock(1); // Only 1 unit left, requesting 2

        when(idempotencyService.generateHash(anyString())).thenReturn("hash");
        when(idempotencyService.isReplay(anyString(), anyString())).thenReturn(false);
        when(productRepository.findByIdWithLock(1L)).thenReturn(Optional.of(activeProduct));
        when(orderRepository.existsByUserIdAndProductIdAndStatusNot(anyLong(), anyLong(), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> orderService.createOrder(validRequest, "KEY-005"))
                .isInstanceOf(InsufficientInventoryException.class);
    }

    @Test
    @DisplayName("Should throw InsufficientInventoryException when concurrent DB update fails")
    void testCreateOrder_concurrentInventoryConflict() {
        // availableStock check passes, but DB atomic update returns 0 (race condition)
        when(idempotencyService.generateHash(anyString())).thenReturn("hash");
        when(idempotencyService.isReplay(anyString(), anyString())).thenReturn(false);
        when(productRepository.findByIdWithLock(1L)).thenReturn(Optional.of(activeProduct));
        when(orderRepository.existsByUserIdAndProductIdAndStatusNot(anyLong(), anyLong(), any()))
                .thenReturn(false);
        when(productRepository.reserveInventory(1L, 2)).thenReturn(0); // <-- race condition

        assertThatThrownBy(() -> orderService.createOrder(validRequest, "KEY-006"))
                .isInstanceOf(InsufficientInventoryException.class);
    }
}
