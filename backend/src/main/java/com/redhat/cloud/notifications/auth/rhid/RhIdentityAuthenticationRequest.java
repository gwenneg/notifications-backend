package com.redhat.cloud.notifications.auth.rhid;

import io.quarkus.security.identity.request.BaseAuthenticationRequest;

import static com.redhat.cloud.notifications.auth.rhid.RHIdentityAuthMechanism.IDENTITY_HEADER;

public class RhIdentityAuthenticationRequest extends BaseAuthenticationRequest {

    public RhIdentityAuthenticationRequest(String xRhIdentity) {
        setAttribute(IDENTITY_HEADER, xRhIdentity);
    }
}
