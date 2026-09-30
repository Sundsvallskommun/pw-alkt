package se.sundsvall.alkt.integration.operaton;

import org.camunda.bpm.client.spring.impl.subscription.SpringTopicSubscriptionImpl;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import static java.util.List.of;
import static se.sundsvall.alkt.Constants.TENANT_ID_ALKT;

/**
 * Several process workers poll the same engine, and a topic name is theirs to choose as well. Without the tenant a
 * subscription fetches their tasks too.
 */
@Component
class TenantAwareSubscriptions implements BeanPostProcessor {

	@Override
	public Object postProcessBeforeInitialization(final Object bean, final String beanName) {
		if (bean instanceof final SpringTopicSubscriptionImpl subscription) {
			subscription.getSubscriptionConfiguration().setTenantIdIn(of(TENANT_ID_ALKT));
		}
		return bean;
	}
}
