package com.redhat.cloud.notifications.db.mappers;

import com.redhat.cloud.notifications.db.entities.EndpointEmailSubscriptionEntity;
import com.redhat.cloud.notifications.models.EmailSubscription;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "cdi")
public interface EmailSubscriptionMapper {

    @Mapping(source = "id.accountId", target = "accountId")
    @Mapping(source = "id.userId", target = "username")
    @Mapping(source = "id.subscriptionType", target = "type")
    @Mapping(source = "id.bundleName", target = "bundle")
    @Mapping(source = "id.applicationName", target = "application")
    EmailSubscription entityToDto(EndpointEmailSubscriptionEntity entity);

    @InheritInverseConfiguration
    EndpointEmailSubscriptionEntity dtoToEntity(EmailSubscription dto);

    default EmailSubscription.EmailSubscriptionType deserializeSubscriptionType(String subscriptionType) {
        if (subscriptionType == null) {
            return null;
        } else {
            return EmailSubscription.EmailSubscriptionType.valueOf(subscriptionType);
        }
    }

    default String serializeSubscriptionType(EmailSubscription.EmailSubscriptionType subscriptionType) {
        if (subscriptionType == null) {
            return null;
        } else {
            return subscriptionType.name();
        }
    }
}
