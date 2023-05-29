package com.redhat.cloud.notifications.processors.camel;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class IncomingCloudEventFilter implements Predicate {

    @ConfigProperty(name = "coucou")
    String bla;

    @Override
    public boolean matches(Exchange exchange) {
        String header = exchange.getIn().getHeader("ce-type", String.class);
        return bla.equals(header);
    }
}
