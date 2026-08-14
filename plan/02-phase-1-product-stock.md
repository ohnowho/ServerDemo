# 02 — Phase 1: Products & stock

**Goal**: product CRUD + **atomic** stock deduction/restoration used by later phases.

## Files

| Op | Path |
|----|------|
| N | `backend/src/main/java/com/example/demo/product/Product.java` |
| N | `backend/src/main/java/com/example/demo/product/ProductRepository.java` |
| N | `backend/src/main/java/com/example/demo/product/ProductService.java` |
| N | `backend/src/main/java/com/example/demo/product/ProductController.java` |
| N | `backend/src/main/java/com/example/demo/product/dto/CreateProductRequest.java` |
| N | `backend/src/main/java/com/example/demo/product/dto/AdjustStockRequest.java` |
| T | `backend/src/test/java/com/example/demo/product/ProductServiceTest.java` |

## Details

### `Product` entity

Fields: `id` (identity), `name` (not blank, unique, length ≤ 100), `priceCents`
(`@Column(nullable=false)` long), `stock` (int, ≥ 0), `status`
(`@Enumerated(STRING)` default `ON_SALE`). `createdAt` for display.

### `ProductRepository` — the core atomic ops

```java
@Modifying
@Query("update Product p set p.stock = p.stock - :qty where p.id = :id and p.stock >= :qty")
int deductStock(@Param("id") long id, @Param("qty") int qty);

@Modifying
@Query("update Product p set p.stock = p.stock + :qty where p.id = :id")
int restoreStock(@Param("id") long id, @Param("qty") int qty);

@Modifying
@Query("update Product p set p.stock = p.stock + :delta where p.id = :id and p.stock + :delta >= 0")
int adjustStock(@Param("id") long id, @Param("delta") int delta);
```

- Each is a single SQL statement → race-free under concurrency.
- Return value = affected row count; **0 means "not applied"** (missing or insufficient).

### `ProductService`

- `create(CreateProductRequest)` → save.
- `list()` → `ON_SALE` only, ordered by id.
- `get(id)` → or throw `BusinessException(NOT_FOUND, "PRODUCT_NOT_FOUND")`.
- `adjustStock(id, delta)` → call `adjustStock`; 0 rows → `NEGATIVE_STOCK` (409).
- `deductStock(id, qty)` → internal (package-visible for `OrderService`); 0 rows →
  `BusinessException(CONFLICT, "INSUFFICIENT_STOCK", "stock ... not enough for qty ...")`.

### `ProductController`

`GET /api/products`, `GET /api/products/{id}`, `POST /api/products` (201),
`PATCH /api/products/{id}/stock`. Validation via `@Valid` DTOs: name not blank,
`priceCents`/`price` > 0 — note: accept `BigDecimal price` (yuan) on the wire, convert with
`Money.yuanToCents`.

## Acceptance criteria

- [ ] `curl` create → 201; list shows only `ON_SALE`.
- [ ] `deductStock` returns 0 when stock insufficient; stock never goes negative.
- [ ] Unit tests: deduct ok / insufficient (0 rows) / adjustStock negative guard.
- [ ] `./mvnw -q test` green for `ProductServiceTest`.
