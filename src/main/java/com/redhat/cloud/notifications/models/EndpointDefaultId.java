package com.redhat.cloud.notifications.models;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class EndpointDefaultId implements Serializable {

    @NotNull
    @Column(name = "account_id")
    public String accountId;

    @NotNull
    @Column(name = "endpoint_id")
    public UUID endpointId;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof EndpointDefaultId) {
            EndpointDefaultId other = (EndpointDefaultId) o;
            return Objects.equals(accountId, other.accountId) &&
                    Objects.equals(endpointId, other.endpointId);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, endpointId);
    }
}
