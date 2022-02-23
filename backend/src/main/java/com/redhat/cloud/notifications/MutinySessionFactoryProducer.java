package com.redhat.cloud.notifications;

import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

public class MutinySessionFactoryProducer {

    @Singleton
    @Produces
    Mutiny.SessionFactory produce() {
        return null;
    }
}
