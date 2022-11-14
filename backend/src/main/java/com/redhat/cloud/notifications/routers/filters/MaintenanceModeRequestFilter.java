package com.redhat.cloud.notifications.routers.filters;

import com.redhat.cloud.notifications.db.repositories.StatusRepository;
import io.quarkus.cache.CacheResult;
import io.quarkus.logging.Log;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Response;

import java.util.List;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_1_0;
import static com.redhat.cloud.notifications.models.Status.MAINTENANCE;
import static javax.ws.rs.core.Response.Status.SERVICE_UNAVAILABLE;

@ApplicationScoped
public class MaintenanceModeRequestFilter {

    // This list contains all request paths that should never be affected by the maintenance mode.
    private static final List<String> NO_MAINTENANCE_REQUEST_PATHS = List.of(
            API_INTERNAL,
            "/health",
            "/metrics",
            API_NOTIFICATIONS_V_1_0 + "/status"
    );

    private static final Response MAINTENANCE_IN_PROGRESS = Response.status(SERVICE_UNAVAILABLE).entity("Maintenance in progress").build();

    @Inject
    StatusRepository statusRepository;

    @ServerRequestFilter
    public Response filter(ContainerRequestContext requestContext) {
        try {
            String requestPath = requestContext.getUriInfo().getRequestUri().getPath();
            System.out.println("==================1");
            Log.tracef("Filtering request to %s", requestPath);
            System.out.println("==================2");

            if (!isAffectedByMaintenanceMode(requestPath)) {
                return null;
            }
            System.out.println("==================3");

            /*
             * If this point is reached, the current request path can be affected by the maintenance mode.
             * Let's check if maintenance is on in the database.
             */
            if (isMaintenance()) {
                System.out.println("==================4");
                Log.trace("Maintenance mode is enabled in the database, aborting request and returning HTTP status 503");
                System.out.println("==================5");
                return MAINTENANCE_IN_PROGRESS;
            } else {
                System.out.println("==================6");
                // This filter work is done. The request will be processed normally.
                return Response.ok().build();
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    boolean isAffectedByMaintenanceMode(String requestPath) {
        // First, we check if the request path should be affected by the maintenance mode.
        for (String noMaintenanceRequestPath : NO_MAINTENANCE_REQUEST_PATHS) {
            if (requestPath.startsWith(noMaintenanceRequestPath)) {
                Log.trace("Request path shouldn't be affected by the maintenance mode, database check will be skipped");
                // This filter work is done. The request will be processed normally.
                return false;
            }
        }
        return true;
    }

    @CacheResult(cacheName = "maintenance")
    public Boolean isMaintenance() {
        return statusRepository.getCurrentStatus().status == MAINTENANCE;
    }
}
