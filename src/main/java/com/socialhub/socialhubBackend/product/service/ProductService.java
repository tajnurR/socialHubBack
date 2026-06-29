package com.socialhub.socialhubBackend.product.service;

import com.socialhub.socialhubBackend.common.exception.ResourceNotFoundException;
import com.socialhub.socialhubBackend.product.domain.Product;
import com.socialhub.socialhubBackend.product.dto.ProductDtos.ProductRequest;
import com.socialhub.socialhubBackend.product.dto.ProductDtos.ProductResponse;
import com.socialhub.socialhubBackend.product.repository.ProductRepository;
import com.socialhub.socialhubBackend.user.context.CurrentUser;
import com.socialhub.socialhubBackend.user.context.CurrentUserProvider;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Demo product catalog CRUD, scoped to the current user. */
@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository repository;
    private final CurrentUserProvider currentUserProvider;

    public ProductService(ProductRepository repository, CurrentUserProvider currentUserProvider) {
        this.repository = repository;
        this.currentUserProvider = currentUserProvider;
    }

    public List<ProductResponse> list() {
        CurrentUser user = currentUserProvider.currentUser();
        return repository
                .findByOrganizationIdAndUserIdOrderByNameAsc(user.organizationId(), user.userId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ProductResponse get(Long id) {
        return toResponse(getOwned(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        CurrentUser user = currentUserProvider.currentUser();
        Product product = new Product();
        product.setOrganizationId(user.organizationId());
        product.setUserId(user.userId());
        apply(product, request);
        return toResponse(repository.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = getOwned(id);
        apply(product, request);
        return toResponse(repository.save(product));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(getOwned(id));
    }

    /** Owned products by id, for cross-service validation (e.g. post → product). */
    public Product getOwned(Long id) {
        CurrentUser user = currentUserProvider.currentUser();
        return repository
                .findByIdAndOrganizationIdAndUserId(id, user.organizationId(), user.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private void apply(Product product, ProductRequest request) {
        product.setName(request.name().trim());
        product.setSku(request.sku());
        product.setDescription(request.description());
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getSku(), p.getDescription(), p.getCreatedAt());
    }
}
