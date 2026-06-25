package com.socialhub.socialhubBackend.integration.core.mapper;

import com.socialhub.socialhubBackend.integration.core.domain.SocialIntegration;
import com.socialhub.socialhubBackend.integration.core.dto.IntegrationResponse;
import org.mapstruct.Mapping;
import org.mapstruct.Mapper;

/** Entity -> response mapper. Never maps the real access token; emits a mask. */
@Mapper(componentModel = "spring")
public interface IntegrationMapper {

    @Mapping(target = "accessTokenMasked", constant = "********")
    IntegrationResponse toResponse(SocialIntegration integration);
}
