package com.redhat.cloud.notifications.routers.engine;

import com.redhat.cloud.notifications.routers.replay.EventsReplayRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@RegisterRestClient(configKey = "internal-engine")
public interface ReplayService {

    @Path(API_INTERNAL + "/replay")
    @POST
    @Consumes(APPLICATION_JSON)
    void replay(EventsReplayRequest eventsReplayRequest);
}