package com.socialhub.socialhubBackend.product.repository;

import com.socialhub.socialhubBackend.product.domain.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByOrganizationIdAndUserIdOrderByNameAsc(Long organizationId, Long userId);

    Optional<Product> findByIdAndOrganizationIdAndUserId(Long id, Long organizationId, Long userId);
}
