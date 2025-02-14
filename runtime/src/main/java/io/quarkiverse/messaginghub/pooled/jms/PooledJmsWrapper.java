package io.quarkiverse.messaginghub.pooled.jms;

import jakarta.jms.ConnectionFactory;
import jakarta.transaction.TransactionManager;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.narayana.jta.jms.JmsXAResourceRecoveryHelper;
import org.jboss.tm.XAResourceRecoveryRegistry;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;
import org.messaginghub.pooled.jms.JmsPoolXAConnectionFactory;

import io.quarkus.arc.Arc;

public class PooledJmsWrapper {
    private boolean transaction;
    private PooledJmsRuntimeConfig pooledJmsRuntimeConfig;

    public PooledJmsWrapper(boolean transaction, PooledJmsRuntimeConfig pooledJmsRuntimeConfig) {
        this.transaction = transaction;
        this.pooledJmsRuntimeConfig = pooledJmsRuntimeConfig;
    }

    public ConnectionFactory wrapConnectionFactory(ConnectionFactory connectionFactory) {
        if (!pooledJmsRuntimeConfig.poolingEnabled) {
            return connectionFactory;
        }

        if (transaction && pooledJmsRuntimeConfig.transaction.equals(TransactionIntegration.XA)) {
            return getXAConnectionFactory(connectionFactory);
        } else if (transaction && pooledJmsRuntimeConfig.transaction.equals(TransactionIntegration.ENABLED)) {
            return getLocalTransactionConnectionFactory(connectionFactory);
        } else {
            return getConnectionFactory(connectionFactory);
        }
    }

    private ConnectionFactory getXAConnectionFactory(ConnectionFactory connectionFactory) {
        TransactionManager transactionManager = Arc.container().instance(TransactionManager.class).get();

        JmsPoolXAConnectionFactory xaConnectionFactory = new JmsPoolXAConnectionFactory();
        xaConnectionFactory.setTransactionManager(transactionManager);
        pooledJmsRuntimeConfigureConnectionFactory(xaConnectionFactory, connectionFactory);

        XAResourceRecoveryRegistry xaResourceRecoveryRegistry = Arc.container().instance(XAResourceRecoveryRegistry.class)
                .get();
        boolean recoveryEnable = ConfigProvider.getConfig().getValue("quarkus.transaction-manager.enable-recovery",
                Boolean.class);

        if (xaResourceRecoveryRegistry != null && recoveryEnable) {
            JmsXAResourceRecoveryHelper recoveryHelper = new JmsXAResourceRecoveryHelper(xaConnectionFactory);
            xaResourceRecoveryRegistry.addXAResourceRecovery(() -> recoveryHelper.getXAResources());
        }

        return xaConnectionFactory;
    }

    private ConnectionFactory getLocalTransactionConnectionFactory(ConnectionFactory connectionFactory) {
        TransactionManager transactionManager = Arc.container().instance(TransactionManager.class).get();

        JmsPoolLocalTransactionConnectionFactory poolLocalTransactionConnectionFactory = new JmsPoolLocalTransactionConnectionFactory();
        poolLocalTransactionConnectionFactory.setTransactionManager(transactionManager);
        pooledJmsRuntimeConfigureConnectionFactory(poolLocalTransactionConnectionFactory, connectionFactory);

        return poolLocalTransactionConnectionFactory;
    }

    private ConnectionFactory getConnectionFactory(ConnectionFactory connectionFactory) {
        JmsPoolConnectionFactory poolConnectionFactory = new JmsPoolConnectionFactory();
        pooledJmsRuntimeConfigureConnectionFactory(poolConnectionFactory, connectionFactory);

        return poolConnectionFactory;
    }

    private void pooledJmsRuntimeConfigureConnectionFactory(JmsPoolConnectionFactory poolConnectionFactory,
            ConnectionFactory connectionFactory) {
        poolConnectionFactory.setConnectionFactory(connectionFactory);
        poolConnectionFactory.setMaxConnections(pooledJmsRuntimeConfig.maxConnections);
        poolConnectionFactory.setConnectionIdleTimeout(pooledJmsRuntimeConfig.connectionIdleTimeout);
        poolConnectionFactory.setConnectionCheckInterval(pooledJmsRuntimeConfig.connectionCheckInterval);
        poolConnectionFactory.setUseProviderJMSContext(pooledJmsRuntimeConfig.useProviderJMSContext);

        poolConnectionFactory.setMaxSessionsPerConnection(pooledJmsRuntimeConfig.maxSessionsPerConnection);
        poolConnectionFactory.setBlockIfSessionPoolIsFull(pooledJmsRuntimeConfig.blockIfSessionPoolIsFull);
        poolConnectionFactory.setBlockIfSessionPoolIsFullTimeout(pooledJmsRuntimeConfig.blockIfSessionPoolIsFullTimeout);
        poolConnectionFactory.setUseAnonymousProducers(pooledJmsRuntimeConfig.useAnonymousProducers);
    }
}
