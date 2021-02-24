package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.StuffHolder;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Liveness;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
@Liveness
public class LivenessService implements HealthCheck {

//    @Inject
//    HealthCenter messagingHealth;

    @Inject
    Uni<PostgresqlConnection> connectionPublisherUni;

//    public Uni<Boolean> getLiveness() {
//        Uni<Boolean> postgresHealth = postgresConnectionHealth();
//        Uni<Boolean> kafkaHealth = Uni.createFrom().item(messagingHealth.getReadiness())
//                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
//                .map(hr -> {
//                    for (HealthReport.ChannelInfo channel : hr.getChannels()) {
//                        if (!channel.isOk()) {
//                            return false;
//                        }
//                    }
//                    return true;
//                });
//
//        return Uni.combine().all().unis(postgresHealth, kafkaHealth).combinedWith((p, k) -> p && k);
//    }

    Uni<Boolean> postgresConnectionHealth() {
        return connectionPublisherUni.flatMap(connection -> {
            return Uni.createFrom().publisher(
                    connection.createStatement("SELECT COUNT(1)")
                            .execute()
                            .flatMap(postgresqlResult -> postgresqlResult.map((row, rowMetadata) -> true))
            ).eventually(() -> Uni.createFrom().publisher(connection.close()));
        }).onFailure().recoverWithItem(false);
    }

    @Override
    public HealthCheckResponse call() {

        boolean adminDown = StuffHolder.getInstance().isAdminDown();

        HealthCheckResponseBuilder response = HealthCheckResponse.named("Notifications readiness check");
        if (adminDown) {
            response.down().withData("status", "admin-down");
        } else {
            boolean dbState = postgresConnectionHealth().await().indefinitely();
            response.state(dbState).withData("reactive-db-check", dbState);
        }

        return response.build();
    }
}
