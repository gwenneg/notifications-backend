package com.redhat.cloud.notifications.connector;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.CamelContext;

@DefaultBean
@ApplicationScoped
public class CamelComponentsConfigurator {

    public void configure(CamelContext camelContext) {

    }
}
