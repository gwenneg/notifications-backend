package com.redhat.cloud.notifications.db;

import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

// TODO NOTIF-491 Delete this class when the migration is complete.
@Path("/internal/denormalizedEvents/migrate")
public class DenormalizedEventsMigrationService {

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @GET
    public Uni<Integer> migrate() {
        return sessionFactory.withTransaction((session, transaction) -> {
            // This query may take a very long time to run, which is why it won't be run at server startup.
            String hql = "UPDATE event e " +
                    "SET e.bundle_id = b.id, e.bundle_display_name = b.display_name, e.application_id = a.id, e.application_display_name = a.display_name, e.event_type_display_name = et.display_name " +
                    "FROM event_type et, applications a, bundles b " +
                    "WHERE e.bundle_id IS NULL AND e.event_type_id = et.id AND et.application_id = a.id AND a.bundle_id = b.id";
            return session.createNativeQuery(hql)
                    .executeUpdate();
        });
    }
}
