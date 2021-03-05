package com.redhat.cloud.notifications.db.mappers;

import com.redhat.cloud.notifications.db.entities.EmailAggregationEntity;
import com.redhat.cloud.notifications.models.EmailAggregation;
import io.vertx.core.json.JsonObject;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "cdi")
public interface EmailAggregationMapper {

    @Mapping(source = "applicationName", target = "application")
    @Mapping(source = "bundleName", target = "bundle")
    EmailAggregation entityToDto(EmailAggregationEntity entity);

    @InheritInverseConfiguration
    EmailAggregationEntity dtoToEntity(EmailAggregation dto);

    default JsonObject deserializePayload(String payload) {
        if (payload == null) {
            return null;
        } else {
            return new JsonObject(payload);
        }
    }

    default String serializePayload(JsonObject payload) {
        if (payload == null) {
            return null;
        } else {
            return payload.encode();
        }
    }
}
