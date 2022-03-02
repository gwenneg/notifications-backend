package com.redhat.cloud.notifications.db;

import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.jboss.logging.Logger;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.persistence.EntityManager;

@RequestScoped
public class StatelessSessionProducer {

    private static final Logger LOGGER = Logger.getLogger(StatelessSessionProducer.class);

    @Inject
    EntityManager entityManager;

    @Produces
    @RequestScoped // ou pas
    // what if ça plante pendant un processing Kafka => session morte ?
    StatelessSession produceStatelessSession() {
        LOGGER.debug("Opening a stateless session");
        return entityManager.unwrap(Session.class).getSessionFactory().openStatelessSession();
    }

    void disposeStatelessSession(@Disposes StatelessSession statelessSession) {
        if (statelessSession != null) {
            LOGGER.debug("Closing a stateless session");
            statelessSession.close();
        }
    }
}
