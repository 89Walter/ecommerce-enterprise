package com.ecommerce.catalog.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Data
@Builder // Lombok genera un builder annidato in Product, che può usare il costruttore privato ovunque.
@AllArgsConstructor(access = AccessLevel.PRIVATE) // genera un costruttore che accetta tutti i campi di Product, ma lo rende private: solo la classe Product può chiamarlo direttamente.
@NoArgsConstructor(access = AccessLevel.PROTECTED) // genera un costruttore senza parametri, con accesso protected. Serve a JPA/Hibernate per ricreare l’entità quando la legge dal database.
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

}
