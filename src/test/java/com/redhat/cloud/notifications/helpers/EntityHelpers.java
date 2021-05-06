package com.redhat.cloud.notifications.helpers;

import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Attributes;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.WebhookAttributes;

import java.util.UUID;

public class EntityHelpers {

    public static Bundle buildBundle(String name, String displayName) {
        Bundle bundle = new Bundle();
        bundle.setName(name);
        bundle.setDisplayName(displayName);
        return bundle;
    }

    public static Application buildApp(String bundleId, String name, String displayName) {
        Application app = new Application();
        if (bundleId != null) {
            app.setBundleId(UUID.fromString(bundleId));
        }
        app.setName(name);
        app.setDisplayName(displayName);
        return app;
    }

    public static EventType buildEventType(String appId, String name, String displayName, String description) {
        EventType eventType = new EventType();
        if (appId != null) {
            eventType.setApplicationId(UUID.fromString(appId));
        }
        eventType.setName(name);
        eventType.setDisplayName(displayName);
        eventType.setDescription(description);
        return eventType;
    }

    public static BehaviorGroup buildBehaviorGroup(String bundleId, String displayName) {
        BehaviorGroup behaviorGroup = new BehaviorGroup();
        if (bundleId != null) {
            behaviorGroup.setBundleId(UUID.fromString(bundleId));
        }
        behaviorGroup.setDisplayName(displayName);
        return behaviorGroup;
    }

    public static Endpoint buildEndpoint(EndpointType type, String name, String description, boolean enabled, Attributes properties) {
        Endpoint endpoint = new Endpoint();
        endpoint.setType(type);
        endpoint.setName(name);
        endpoint.setDescription(description);
        endpoint.setEnabled(enabled);
        endpoint.setProperties(properties);
        return endpoint;
    }

    public static WebhookAttributes buildWebhookAttributes(HttpType method, boolean disableSslVerification, String secretToken, String url) {
        WebhookAttributes attributes = new WebhookAttributes();
        attributes.setMethod(method);
        attributes.setDisableSSLVerification(disableSslVerification);
        attributes.setSecretToken(secretToken);
        attributes.setUrl(url);
        return attributes;
    }
}
