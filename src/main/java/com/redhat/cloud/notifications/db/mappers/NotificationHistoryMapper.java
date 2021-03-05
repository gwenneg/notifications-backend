package com.redhat.cloud.notifications.db.mappers;

import com.redhat.cloud.notifications.db.entities.EndpointEntity;
import com.redhat.cloud.notifications.db.entities.NotificationHistoryEntity;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.NotificationHistory;
import io.vertx.core.json.JsonObject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import javax.inject.Inject;

@Mapper(componentModel = "cdi")
public abstract class NotificationHistoryMapper {

    @Inject
    Mutiny.Session session;

    @Mapping(source = "accountId", target = "tenant")
    public abstract NotificationHistory entityToDto(NotificationHistoryEntity entity);

    @InheritInverseConfiguration
    public abstract NotificationHistoryEntity dtoToEntity(NotificationHistory dto);

    protected JsonObject deserializeDetails(String details) {
        if (details == null) {
            return null;
        } else {
            return new JsonObject(details);
        }
    }

    protected String serializeDetails(JsonObject details) {
        if (details == null) {
            return null;
        } else {
            return details.encode();
        }
    }

    protected EndpointEntity getEndpointEntityReference(Endpoint endpoint) {
        return session.getReference(EndpointEntity.class, endpoint.getId());
    }
}
