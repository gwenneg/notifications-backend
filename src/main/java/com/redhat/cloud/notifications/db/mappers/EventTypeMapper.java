package com.redhat.cloud.notifications.db.mappers;

import com.redhat.cloud.notifications.db.entities.EventTypeEntity;
import com.redhat.cloud.notifications.models.EventType;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "cdi")
public interface EventTypeMapper {

    @Mapping(source = "displayName", target = "display_name")
    EventType entityToDto(EventTypeEntity entity);

    @InheritInverseConfiguration
    EventTypeEntity dtoToEntity(EventType dto);
}
