package com.redhat.cloud.notifications.connector.http;

import com.redhat.cloud.notifications.connector.ConnectorConfig;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class HttpConnectorConfig extends ConnectorConfig {

    private static final String HTTP_COMPONENTS = "notifications.connector.http.components";
    private static final String HTTP_CONNECT_TIMEOUT_MS = "notifications.connector.http.connect-timeout-ms";
    private static final String HTTP_CONNECTIONS_PER_ROUTE = "notifications.connector.http.connections-per-route";
    private static final String HTTP_FOLLOW_REDIRECTS = "notifications.connector.http.follow-redirects";
    private static final String HTTP_MAX_TOTAL_CONNECTIONS = "notifications.connector.http.max-total-connections";
    private static final String HTTP_SOCKET_TIMEOUT_MS = "notifications.connector.http.socket-timeout-ms";

    @ConfigProperty(name = HTTP_COMPONENTS, defaultValue = "http,https")
    List<String> httpComponents;

    @ConfigProperty(name = HTTP_CONNECT_TIMEOUT_MS, defaultValue = "2500")
    int httpConnectTimeout;

    @ConfigProperty(name = HTTP_CONNECTIONS_PER_ROUTE, defaultValue = "20")
    int httpConnectionsPerRoute;

    @ConfigProperty(name = HTTP_FOLLOW_REDIRECTS, defaultValue = "false")
    boolean followRedirects;

    @ConfigProperty(name = HTTP_MAX_TOTAL_CONNECTIONS, defaultValue = "200")
    int httpMaxTotalConnections;

    @ConfigProperty(name = HTTP_SOCKET_TIMEOUT_MS, defaultValue = "2500")
    int httpSocketTimeout;

    @Override
    protected Map<String, Object> getLoggedConfiguration() {
        Map<String, Object> config = super.getLoggedConfiguration();
        config.put(HTTP_COMPONENTS, httpComponents);
        config.put(HTTP_CONNECT_TIMEOUT_MS, httpConnectTimeout);
        config.put(HTTP_CONNECTIONS_PER_ROUTE, httpConnectionsPerRoute);
        config.put(HTTP_FOLLOW_REDIRECTS, followRedirects);
        config.put(HTTP_MAX_TOTAL_CONNECTIONS, httpMaxTotalConnections);
        config.put(HTTP_SOCKET_TIMEOUT_MS, httpSocketTimeout);
        return config;
    }

    public List<String> getHttpComponents() {
        return httpComponents;
    }

    public int getHttpConnectTimeout() {
        return httpConnectTimeout;
    }

    public int getHttpConnectionsPerRoute() {
        return httpConnectionsPerRoute;
    }

    public boolean isFollowRedirects() {
        return followRedirects;
    }

    public int getHttpMaxTotalConnections() {
        return httpMaxTotalConnections;
    }

    public int getHttpSocketTimeout() {
        return httpSocketTimeout;
    }
}
