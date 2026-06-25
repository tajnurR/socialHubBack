package com.socialhub.socialhubBackend.tenant.mapper;

import com.socialhub.socialhubBackend.tenant.domain.Organization;
import com.socialhub.socialhubBackend.tenant.dto.OrganizationResponse;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper: entity -> response DTO. Implementation is generated at build
 * time and registered as a Spring bean ({@code componentModel = "spring"}).
 *
 * <p>This is the reference pattern for keeping DTOs separate from entities — add
 * one mapper per aggregate as the domain grows.
 */
@Mapper(componentModel = "spring")
public interface OrganizationMapper {

    OrganizationResponse toResponse(Organization organization);
}
