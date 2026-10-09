package com.ecommerce.catalog.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("""
            SELECT p FROM Product p
            WHERE :search IS NULL
               OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :search, '%'))
            """)
    Page<Product> search(@Param("search") String search, Pageable pageable);
}
