package com.example.demo.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.demo.common.BusinessException;
import com.example.demo.product.dto.CreateProductRequest;
import com.example.demo.product.dto.ProductResponse;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    ProductRepository productRepository;

    @InjectMocks
    ProductService productService;

    @Test
    void createConvertsYuanToCents() {
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse response = productService.create(new CreateProductRequest("Lamp", new BigDecimal("19.99"), 10));

        assertThat(response.price()).isEqualByComparingTo("19.99");
        assertThat(response.stock()).isEqualTo(10);
        assertThat(response.status()).isEqualTo(ProductStatus.ON_SALE);
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void listReturnsOnlyOnSaleProducts() {
        Product onSale = new Product("Lamp", 1000, 5);
        when(productRepository.findByStatusOrderByIdAsc(ProductStatus.ON_SALE))
                .thenReturn(List.of(onSale));

        List<ProductResponse> products = productService.listOnSale();

        assertThat(products).hasSize(1);
        assertThat(products.get(0).name()).isEqualTo("Lamp");
    }

    @Test
    void deductStockRejectsInsufficientStock() {
        when(productRepository.deductStock(1L, 99)).thenReturn(0);
        when(productRepository.findById(1L)).thenReturn(Optional.of(new Product("Lamp", 1000, 3)));

        assertThatThrownBy(() -> productService.deductStock(1L, 99))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INSUFFICIENT_STOCK"));
    }

    @Test
    void deductStockAcceptsWhenEnoughStock() {
        when(productRepository.deductStock(1L, 2)).thenReturn(1);

        productService.deductStock(1L, 2);

        verify(productRepository).deductStock(1L, 2);
    }

    @Test
    void adjustStockRejectsNegativeResult() {
        when(productRepository.adjustStock(1L, -100)).thenReturn(0);

        assertThatThrownBy(() -> productService.adjustStock(1L, -100))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("NEGATIVE_STOCK"));
    }

    @Test
    void getUnknownProductThrowsNotFound() {
        when(productRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.get(42L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("PRODUCT_NOT_FOUND"));
    }

    @Test
    void restoreStockNeverFailsSilently() {
        when(productRepository.restoreStock(1L, 2)).thenReturn(1);

        productService.restoreStock(1L, 2);

        verify(productRepository, never()).findById(any());
    }
}
