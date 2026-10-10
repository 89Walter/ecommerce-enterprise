package com.ecommerce.catalog.product;

import com.ecommerce.catalog.exception.ProductNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public ProductResponse create(ProductRequest request) {
        Product savedProduct = productRepository.save(productMapper.toEntity(request));
        return productMapper.toResponse(savedProduct);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> findAll(String search, Pageable pageable) {
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        return productRepository.search(normalizedSearch, pageable).map(productMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return productMapper.toResponse(getProduct(id));
    }

    public ProductResponse update(Long id, ProductRequest request) {
        Product product = getProduct(id);
        productMapper.updateEntity(product, request);
        return productMapper.toResponse(productRepository.save(product));
    }

    public void delete(Long id) {
        productRepository.delete(getProduct(id));
    }

    private Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }
}
