package com.redhat.cloud.notifications.db.mappers;

import com.redhat.cloud.notifications.db.entities.ApplicationEntity;
import com.redhat.cloud.notifications.db.entities.BundleEntity;
import com.redhat.cloud.notifications.models.Application;
import org.hibernate.reactive.mutiny.Mutiny;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;

import javax.inject.Inject;
import java.util.UUID;

@Mapper(componentModel = "cdi")
public abstract class ApplicationMapper {

    @Inject
    Mutiny.Session session;

    @Mapping(source = "bundle.id", target = "bundleId")
    @Mapping(source = "displayName", target = "display_name")
    public abstract Application entityToDto(ApplicationEntity entity);

    @Mappings({
            @Mapping(source = "bundle.id", target = "bundleId"),
            @Mapping(target = "created", ignore = true),
            @Mapping(target = "updated", ignore = true)
    })
    public abstract Application entityToDtoWithoutTimestamps(ApplicationEntity entity);

    @Mapping(source = "display_name", target = "displayName")
    @Mapping(source = "bundleId", target = "bundle", qualifiedByName = "bundleEntity")
    public abstract ApplicationEntity dtoToEntity(Application dto);

    @Named("bundleEntity")
    protected BundleEntity getBundleEntityReference(UUID bundleId) {
        return session.getReference(BundleEntity.class, bundleId);
    }
}
