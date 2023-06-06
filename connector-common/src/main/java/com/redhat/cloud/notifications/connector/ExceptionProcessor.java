package com.redhat.cloud.notifications.connector;

import io.quarkus.arc.DefaultBean;
import io.quarkus.logging.Log;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import java.io.IOException;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.OUTCOME;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.SUCCESSFUL;
import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.CONNECTOR_TO_ENGINE;
import static org.apache.camel.Exchange.EXCEPTION_CAUGHT;

@DefaultBean
@ApplicationScoped
public class ExceptionProcessor implements Processor {

    private static final String LOG_MSG = "Message sending failed on %s: [orgId=%s, historyId=%s]\n${exception.stacktrace}";

    @Inject
    ProducerTemplate producerTemplate;

    @Override
    public void process(Exchange exchange) {

        Throwable t = exchange.getProperty(EXCEPTION_CAUGHT, Throwable.class);

        exchange.setProperty(SUCCESSFUL, false);
        exchange.setProperty(OUTCOME, t.getMessage());

        String routeId = exchange.getFromRouteId();
        String orgId = exchange.getProperty(ORG_ID, String.class);
        String historyId = exchange.getProperty(ID, String.class);

        log(t, routeId, orgId, historyId);

        producerTemplate.send("direct:" + CONNECTOR_TO_ENGINE, exchange);
    }

    protected void log(Throwable t, String routeId, String orgId, String historyId) {
        if (t instanceof IOException) {
            Log.errorf(LOG_MSG, routeId, orgId, historyId);
        }
    }
}
