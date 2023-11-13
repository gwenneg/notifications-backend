package com.redhat.cloud.notifications.connector.http;

import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.jboss.logging.Logger;

import static org.jboss.logging.Logger.Level.DEBUG;
import static org.jboss.logging.Logger.Level.ERROR;

@ApplicationScoped
public class HttpExceptionProcessor extends ExceptionProcessor {

    private static final String HTTP_ERROR_LOG_MSG = "Message sending failed [routeId=%s, orgId=%s, historyId=%s, targetUrl=%s, statusCode=%d, responseBody=%s]";

    @Override
    protected void process(Throwable t, Exchange exchange) {
        if (t instanceof HttpOperationFailedException e) {
            if (e.getStatusCode() >= 400 && e.getStatusCode() < 500 && e.getStatusCode() != 429) {
                // TODO Disable the integration immediately.
                logHttpError(DEBUG, e, exchange);
            } else if (e.getStatusCode() == 429 || e.getStatusCode() >= 500) {
                // TODO Disable the integration in case of repeated failures.
                logHttpError(DEBUG, e, exchange);
            } else {
                logHttpError(ERROR, e, exchange);
            }
        } else {
            logDefault(t, exchange);
        }
    }

    private void logHttpError(Logger.Level level, HttpOperationFailedException e, Exchange exchange) {
        Log.logf(
                level,
                HTTP_ERROR_LOG_MSG,
                getRouteId(exchange),
                getOrgId(exchange),
                getExchangeId(exchange),
                getTargetUrl(exchange),
                e.getStatusCode(),
                e.getResponseBody()
        );
    }
}
