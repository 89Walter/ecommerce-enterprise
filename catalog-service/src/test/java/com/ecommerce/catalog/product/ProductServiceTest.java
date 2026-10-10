package com.ecommerce.catalog.product;

import com.ecommerce.catalog.exception.ProductNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ProductServiceTest {

    private ProductRepository productRepository;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        productService = new ProductService(productRepository, new ProductMapper());
    }

    @Test
    void createsProductAndReturnsItsResponse() {
        ProductRequest request = request(" Keyboard ", "Mechanical keyboard", "49.90");
        given(productRepository.save(any(Product.class))).willAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(1L);
            product.setCreatedAt(LocalDateTime.parse("2026-10-09T10:00:00"));
            return product;
        });

        ProductResponse response = productService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Keyboard");
        assertThat(response.description()).isEqualTo("Mechanical keyboard");
        assertThat(response.price()).isEqualByComparingTo("49.90");
        assertThat(response.createdAt()).isEqualTo(LocalDateTime.parse("2026-10-09T10:00:00"));
    }

    @Test
    void findsAllProductsAndTrimsSearchText() {
        Pageable pageable = PageRequest.of(0, 20);
        Product product = product(1L, "Keyboard");
        given(productRepository.search("keyboard", pageable))
                .willReturn(new PageImpl<>(List.of(product), pageable, 1));

        Page<ProductResponse> response = productService.findAll(" keyboard ", pageable);

        assertThat(response.getContent()).singleElement()
                .extracting(ProductResponse::name)
                .isEqualTo("Keyboard");
        verify(productRepository).search("keyboard", pageable);
    }

    @Test
    void findsAllProductsWithoutSearchWhenSearchIsNullOrBlank() {
        Pageable pageable = PageRequest.of(0, 20);
        given(productRepository.search(null, pageable)).willReturn(Page.empty(pageable));

        assertThat(productService.findAll(null, pageable)).isEmpty();
        assertThat(productService.findAll("   ", pageable)).isEmpty();

        verify(productRepository, times(2)).search(null, pageable);
    }

    @Test
    void findsProductByIdAndReturnsItsResponse() {
        given(productRepository.findById(1L)).willReturn(Optional.of(product(1L, "Keyboard")));

        ProductResponse response = productService.findById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Keyboard");
    }

    @Test
    void throwsProductNotFoundWhenIdDoesNotExist() {
        given(productRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(999L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("Prodotto con ID 999 non trovato.");
    }

    @Test
    void updatesProductAndReturnsUpdatedResponse() {
        Product product = product(1L, "Old name");
        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(productRepository.save(any(Product.class))).willAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.update(
                1L,
                request(" Updated keyboard ", "Updated description", "59.90")
        );

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Updated keyboard");
        assertThat(response.description()).isEqualTo("Updated description");
        assertThat(response.price()).isEqualByComparingTo("59.90");
        verify(productRepository).save(product);
    }

    @Test
    void deletesProductById() {
        Product product = product(1L, "Keyboard");
        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        productService.delete(1L);

        verify(productRepository).delete(product);
    }

    private static ProductRequest request(String name, String description, String price) {
        return ProductRequest.builder()
                .name(name)
                .description(description)
                .price(new BigDecimal(price))
                .build();
    }

    private static Product product(Long id, String name) {
        return Product.builder()
                .id(id)
                .name(name)
                .description("Product description")
                .price(new BigDecimal("49.90"))
                .createdAt(LocalDateTime.parse("2026-10-09T10:00:00"))
                .build();
    }
}
