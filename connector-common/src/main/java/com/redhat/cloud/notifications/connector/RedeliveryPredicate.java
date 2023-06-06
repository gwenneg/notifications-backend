package com.redhat.cloud.notifications.connector;

import io.quarkus.arc.DefaultBean;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;

import javax.enterprise.context.ApplicationScoped;

import java.io.IOException;

import static org.apache.camel.Exchange.EXCEPTION_CAUGHT;

@DefaultBean
@ApplicationScoped
public class RedeliveryPredicate implements Predicate {

    @Override
    public boolean matches(Exchange exchange) {
        Throwable t = exchange.getProperty(EXCEPTION_CAUGHT, Throwable.class);
        return t instanceof IOException;
    }

    public boolean doesNotMatch(Exchange exchange) {
        return !matches(exchange);
    }
}
