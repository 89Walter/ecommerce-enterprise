package com.ecommerce.catalog.product;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public ProductResponse create(ProductRequest request) {

        // mappi la request con il dao
        Product product = mappingRequestWithDao(request);

        // salvataggio sul db
        Product savedProduct = productRepository.save(product);

        // mi ritorna la response in json
        return mappingResponseWithProductSaved(savedProduct);
    }

    private static Product mappingRequestWithDao(ProductRequest request) {

        return Product.builder()
                .name(request.name().trim())
                .description(request.description())
                .price(request.price())
                .build();
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> findAll(String search, Pageable pageable) {
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        return productRepository.search(normalizedSearch, pageable).map(this::mappingResponseWithProductSaved);
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return mappingResponseWithProductSaved(getProduct(id));
    }

    public ProductResponse update(Long id, ProductRequest request) {
        Product product = getProduct(id);
        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.setPrice(request.price());
        return mappingResponseWithProductSaved(productRepository.save(product));
    }

    public void delete(Long id) {
        productRepository.delete(getProduct(id));
    }

    private Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    private ProductResponse mappingResponseWithProductSaved(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .createdAt(product.getCreatedAt())
                .build();
    }
}
