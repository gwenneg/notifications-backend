package com.redhat.cloud.notifications.connector.http;

import com.redhat.cloud.notifications.connector.CamelComponentsConfigurator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.component.http.HttpComponent;
import org.apache.hc.core5.util.Timeout;

@ApplicationScoped
public class HttpCamelComponentsConfigurator extends CamelComponentsConfigurator {

    @Inject
    HttpConnectorConfig connectorConfig;

    @Override
    public void configure(CamelContext camelContext) {
        for (String httpComponent : connectorConfig.getHttpComponents()) {
            configureTimeouts(camelContext, httpComponent);
        }
    }

    private void configureTimeouts(CamelContext camelContext, String httpComponent) {
        HttpComponent component = camelContext.getComponent(httpComponent, HttpComponent.class);
        component.setConnectTimeout(Timeout.ofMilliseconds(connectorConfig.getHttpConnectTimeout()));
        component.setConnectionsPerRoute(connectorConfig.getHttpConnectionsPerRoute());
        component.setFollowRedirects(connectorConfig.isFollowRedirects());
        component.setMaxTotalConnections(connectorConfig.getHttpMaxTotalConnections());
        component.setSoTimeout(Timeout.ofMilliseconds(connectorConfig.getHttpSocketTimeout()));
    }
}
