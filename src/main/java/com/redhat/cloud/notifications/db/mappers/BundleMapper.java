package com.redhat.cloud.notifications.db.mappers;

import com.redhat.cloud.notifications.db.entities.BundleEntity;
import com.redhat.cloud.notifications.models.Bundle;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "cdi")
public interface BundleMapper {

    @Mapping(source = "displayName", target = "display_name")
    Bundle entityToDto(BundleEntity entity);

    @InheritInverseConfiguration
    BundleEntity dtoToEntity(Bundle dto);
}
