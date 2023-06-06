package com.redhat.cloud.notifications.connector.google.chat;

import com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder;

import javax.enterprise.context.ApplicationScoped;

import java.net.URLDecoder;

import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.CONNECTOR_TO_ENGINE;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.OUTCOME;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.SUCCESSFUL;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.WEBHOOK_URL;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.HTTP_METHOD;

@ApplicationScoped
public class GoogleChatRouteBuilder extends EngineToConnectorRouteBuilder {

    public static final String GOOGLE_CHAT_ROUTE = "google-chat";

    @Override
    public void configureRoute() {

        from(direct(ENGINE_TO_CONNECTOR))
                .routeId(GOOGLE_CHAT_ROUTE)
                .removeHeaders(CAMEL_HTTP_HEADERS_PATTERN)
                .setHeader(HTTP_METHOD, constant(POST))
                .setHeader(CONTENT_TYPE, constant(APPLICATION_JSON))
                /*
                 * Webhook urls provided by Google have already encoded parameters, by default, Camel will also encode endpoint urls.
                 * Parameters such as token values, won't be usable if they are encoded twice.
                 * To avoid double encoding, Camel provides the `RAW()` instruction. It can be applied to parameters values but not on full url.
                 * That involve to split Urls parameters, surround each value by `RAW()` instruction, then concat all those to rebuild endpoint url.
                 * To avoid all those steps, we decode the full url, then Camel will encode it to send the expected format to Google servers.
                 */
                .toD(URLDecoder.decode("${exchangeProperty." + WEBHOOK_URL + "}", UTF_8), maxEndpointCacheSize)
                .setProperty(SUCCESSFUL, constant(true))
                .setProperty(OUTCOME, simple("Event ${exchangeProperty." + ID + "} sent successfully"))
                .to(direct(CONNECTOR_TO_ENGINE));
    }
}
