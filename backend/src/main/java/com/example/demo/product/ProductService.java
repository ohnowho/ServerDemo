package com.example.demo.product;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.common.BusinessException;
import com.example.demo.common.Money;
import com.example.demo.product.dto.CreateProductRequest;
import com.example.demo.product.dto.ProductResponse;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        Product product = new Product(request.name(), Money.yuanToCents(request.price()), request.stock());
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listOnSale() {
        return productRepository.findByStatusOrderByIdAsc(ProductStatus.ON_SALE).stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse get(long id) {
        return ProductResponse.from(requireEntity(id));
    }

    @Transactional
    public ProductResponse adjustStock(long id, int delta) {
        int rows = productRepository.adjustStock(id, delta);
        if (rows == 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "NEGATIVE_STOCK", "stock would become negative for product " + id);
        }
        return ProductResponse.from(requireEntity(id));
    }

    /** Atomic deduction; used by order creation. Throws when stock is insufficient. */
    @Transactional
    public void deductStock(long id, int qty) {
        int rows = productRepository.deductStock(id, qty);
        if (rows == 0) {
            Product product = requireEntity(id);
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "INSUFFICIENT_STOCK",
                    "stock " + product.getStock() + " of " + product.getName() + " is not enough for qty " + qty);
        }
    }

    /** Returns stock to the pool when an order is cancelled or times out. */
    @Transactional
    public void restoreStock(long id, int qty) {
        productRepository.restoreStock(id, qty);
    }

    /** Entity lookup for order creation; the caller runs inside its own transaction. */
    @Transactional
    public Product requireEntity(long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "product not found: " + id));
    }
}
