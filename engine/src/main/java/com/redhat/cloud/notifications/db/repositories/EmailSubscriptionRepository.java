package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import org.hibernate.StatelessSession;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;

@ApplicationScoped
public class EmailSubscriptionRepository {

    @Inject
    StatelessSession statelessSession;

    public List<String> getEmailSubscribersUserId(String accountNumber, String bundleName, String applicationName, EmailSubscriptionType subscriptionType) {
        String query = "SELECT es.id.userId FROM EmailSubscription es WHERE id.accountId = :accountId AND application.bundle.name = :bundleName " +
                "AND application.name = :applicationName AND id.subscriptionType = :subscriptionType";
        return statelessSession.createQuery(query, String.class)
                .setParameter("accountId", accountNumber)
                .setParameter("bundleName", bundleName)
                .setParameter("applicationName", applicationName)
                .setParameter("subscriptionType", subscriptionType)
                .getResultList();
    }
}
