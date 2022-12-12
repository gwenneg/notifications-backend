package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.DelayedThrower;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.processors.camel.CamelTypeProcessor;
import com.redhat.cloud.notifications.processors.email.EmailSubscriptionTypeProcessor;
import com.redhat.cloud.notifications.processors.rhose.RhoseTypeProcessor;
import com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class EndpointProcessor {

    public static final String PROCESSED_MESSAGES_COUNTER_NAME = "processor.input.processed";
    public static final String PROCESSED_ENDPOINTS_COUNTER_NAME = "processor.input.endpoint.processed";
    public static final String DELAYED_EXCEPTION_MSG = "Exceptions were thrown during an event processing";

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    WebhookTypeProcessor webhookProcessor;

    @Inject
    CamelTypeProcessor camelProcessor;

    @Inject
    RhoseTypeProcessor rhoseProcessor;

    @Inject
    EmailSubscriptionTypeProcessor emailProcessor;

    @Inject
    MeterRegistry registry;

    private Counter processedItems;
    private Counter endpointTargeted;

    @PostConstruct
    void init() {
        processedItems = registry.counter(PROCESSED_MESSAGES_COUNTER_NAME);
        endpointTargeted = registry.counter(PROCESSED_ENDPOINTS_COUNTER_NAME);
    }

    public void process(Event event) {
        processedItems.increment();

        List<Endpoint> endpoints = endpointRepository.getTargetEndpoints(event.getOrgId(), event.getEventType());
        endpointTargeted.increment(endpoints.size());

        // Target endpoints are grouped by endpoint type.
        Map<EndpointType, List<Endpoint>> endpointsByType = endpoints.stream().collect(Collectors.groupingBy(Endpoint::getType));

        DelayedThrower.throwEventually(DELAYED_EXCEPTION_MSG, accumulator -> {
            for (Map.Entry<EndpointType, List<Endpoint>> entry : endpointsByType.entrySet()) {
                try {
                    // TODO Create a new endpoint type for RHOSE.
                    if (EndpointType.CAMEL.equals(entry.getKey())) {
                        Map<String, List<Endpoint>> b = entry.getValue().stream().collect(Collectors.groupingBy(Endpoint::getSubType));
                        for (Map.Entry<String, List<Endpoint>> a : b.entrySet()) {
                            try {
                                switch (a.getKey()) {
                                    case "slack":
                                        rhoseProcessor.process(event, entry.getValue());
                                        break;
                                    default:
                                        camelProcessor.process(event, entry.getValue());
                                        break;
                                }
                            } catch (Exception e) {
                                accumulator.add(e);
                            }
                        }
                    } else {
                        // For each endpoint type, the list of target endpoints is sent alongside with the event to the relevant processor.
                        switch (entry.getKey()) {
                            case CAMEL:
                                camelProcessor.process(event, entry.getValue());
                                break;
                            case EMAIL_SUBSCRIPTION:
                                emailProcessor.process(event, entry.getValue());
                                break;
                            case WEBHOOK:
                                webhookProcessor.process(event, entry.getValue());
                                break;
                            default:
                                throw new IllegalArgumentException("Unexpected endpoint type: " + entry.getKey());
                        }
                    }
                } catch (Exception e) {
                    accumulator.add(e);
                }
            }
        });
    }
}
