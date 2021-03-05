package com.redhat.cloud.notifications.models.filter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.redhat.cloud.notifications.models.EventType;
import org.jboss.logging.Logger;

public class ApiResponseFilter extends SimpleBeanPropertyFilter {

    public static final String NAME = "ApiResponseFilter";

    private static final Logger LOGGER = Logger.getLogger(ApiResponseFilter.class);

    @Override
    public void serializeAsField(Object pojo, JsonGenerator jgen, SerializerProvider provider, PropertyWriter writer) throws Exception {
        if (pojo instanceof EventType) {
            EventType eventType = (EventType) pojo;
            switch (writer.getName()) {
                case "application":
                    if (eventType.isFilterOutApplication()) {
                        logFilterOut(EventType.class.getName(), "application");
                        return;
                    }
                    break;
                default:
                    // Do nothing.
                    break;
            }
        }
        // The property was not filtered out, it will be serialized.
        writer.serializeAsField(pojo, jgen, provider);
    }

    private void logFilterOut(String className, String fieldName) {
        LOGGER.debugf("Filtering out %s#%s from a JSON response", className, fieldName);
    }
}
