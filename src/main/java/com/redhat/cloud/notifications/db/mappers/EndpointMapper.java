package com.redhat.cloud.notifications.db.mappers;

import com.redhat.cloud.notifications.db.entities.EndpointEntity;
import com.redhat.cloud.notifications.db.entities.EndpointWebhookEntity;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.WebhookAttributes;
import io.vertx.core.json.Json;
import org.mapstruct.AfterMapping;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import static com.redhat.cloud.notifications.models.Endpoint.EndpointType.WEBHOOK;

@Mapper(componentModel = "cdi")
public interface EndpointMapper {

    @Mapping(source = "accountId", target = "tenant")
    @Mapping(source = "endpointType", target = "type")
    Endpoint entityToDto(EndpointEntity entity);

    @InheritInverseConfiguration
    @Mapping(target = "webhook", ignore = true)
    EndpointEntity dtoToEntity(Endpoint dto);

    default Endpoint.EndpointType deserializeEndpointType(Integer endpointType) {
        if (endpointType == null) {
            return null;
        } else {
            return Endpoint.EndpointType.values()[endpointType];
        }
    }

    default Integer serializeEndpointType(Endpoint.EndpointType endpointType) {
        if (endpointType == null) {
            return null;
        } else {
            return endpointType.ordinal();
        }
    }

    @AfterMapping
    default void mapWebhookProperties(Endpoint dto, @MappingTarget EndpointEntity endpointEntity) {
        if (dto.getProperties() != null && dto.getType() == WEBHOOK) {
            WebhookAttributes attr = (WebhookAttributes) dto.getProperties();
            EndpointWebhookEntity endpointWebhookEntity = new EndpointWebhookEntity();
            endpointWebhookEntity.endpoint = endpointEntity;
            endpointWebhookEntity.url = attr.getUrl();
            endpointWebhookEntity.method = attr.getMethod().name();
            endpointWebhookEntity.disableSslVerification = attr.isDisableSSLVerification();
            endpointWebhookEntity.secretToken = attr.getSecretToken();
            if (attr.getBasicAuthentication() != null) {
                endpointWebhookEntity.basicAuthentication = Json.encode(attr.getBasicAuthentication());
            }
            endpointEntity.webhook = endpointWebhookEntity;
        }
    }

    @AfterMapping
    default void mapWebhookProperties2(EndpointEntity endpointEntity, @MappingTarget Endpoint dto) {
        if (dto.getType() == WEBHOOK) {

            WebhookAttributes attr = new WebhookAttributes();
            attr.setId(endpointEntity.webhook.id);
            attr.setDisableSSLVerification(endpointEntity.webhook.disableSslVerification);
            attr.setSecretToken(endpointEntity.webhook.secretToken);
            String method = endpointEntity.webhook.method;
            attr.setMethod(WebhookAttributes.HttpType.valueOf(method));
            attr.setUrl(endpointEntity.webhook.url);

            String basicAuthentication = endpointEntity.webhook.basicAuthentication;
            if (basicAuthentication != null) {
                attr.setBasicAuthentication(Json.decodeValue(basicAuthentication, WebhookAttributes.BasicAuthentication.class));
            }

            dto.setProperties(attr);

        }
    }

    //FIXME il manque l'inverse
}
