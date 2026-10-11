package com.ecommerce.order.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Page<PurchaseOrder> findByCustomerId(String customerId, Pageable pageable);

    Page<PurchaseOrder> findByStatus(OrderStatus status, Pageable pageable);
}
