package com.flashsale.repository;

import com.flashsale.model.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // Pessimistic write lock — only ONE transaction can hold this lock at a time
    // This is critical for concurrency: prevents overselling
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") Long id);

    // Atomic inventory reservation using DB-level UPDATE with condition check
    @Modifying
    @Query("UPDATE Product p SET p.availableStock = p.availableStock - :qty, " +
           "p.reservedStock = p.reservedStock + :qty " +
           "WHERE p.id = :id AND p.availableStock >= :qty")
    int reserveInventory(@Param("id") Long id, @Param("qty") int qty);

    // Release reserved inventory (on failure/expiry)
    @Modifying
    @Query("UPDATE Product p SET p.availableStock = p.availableStock + :qty, " +
           "p.reservedStock = p.reservedStock - :qty " +
           "WHERE p.id = :id AND p.reservedStock >= :qty")
    int releaseInventory(@Param("id") Long id, @Param("qty") int qty);

    // Confirm inventory (move from reserved → sold, reduce reservedStock)
    @Modifying
    @Query("UPDATE Product p SET p.reservedStock = p.reservedStock - :qty, " +
           "p.totalStock = p.totalStock - :qty " +
           "WHERE p.id = :id AND p.reservedStock >= :qty")
    int confirmInventory(@Param("id") Long id, @Param("qty") int qty);
}
