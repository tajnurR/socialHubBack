package com.socialhub.socialhubBackend.tenant.service;

import com.socialhub.socialhubBackend.common.exception.ResourceNotFoundException;
import com.socialhub.socialhubBackend.tenant.dto.OrganizationResponse;
import com.socialhub.socialhubBackend.tenant.mapper.OrganizationMapper;
import com.socialhub.socialhubBackend.tenant.repository.OrganizationRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for organizations. Demonstrates the controller -> service ->
 * repository layering with entity-to-DTO mapping kept out of the web layer.
 */
@Service
@Transactional(readOnly = true)
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMapper organizationMapper;

    public OrganizationService(
            OrganizationRepository organizationRepository, OrganizationMapper organizationMapper) {
        this.organizationRepository = organizationRepository;
        this.organizationMapper = organizationMapper;
    }

    public List<OrganizationResponse> findAll() {
        return organizationRepository.findAll().stream()
                .map(organizationMapper::toResponse)
                .toList();
    }

    public OrganizationResponse findById(Long id) {
        return organizationRepository.findById(id)
                .map(organizationMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", id));
    }
}
