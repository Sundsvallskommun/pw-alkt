package se.sundsvall.alkt.integration.operaton;

import org.camunda.bpm.client.ExternalTaskClient;
import org.camunda.bpm.client.impl.ExternalTaskClientImpl;
import org.camunda.bpm.client.topic.TopicSubscription;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import se.sundsvall.alkt.Application;

import static java.util.List.of;
import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.alkt.Constants.TENANT_ID_ALKT;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("junit")
class TenantAwareSubscriptionsTest {

	@Autowired
	private ExternalTaskClient client;

	/** Read from the subscriptions the client opened, since a tenant set after opening would never reach a fetch. */
	@Test
	void everyOpenedSubscriptionIsScopedToTheTenant() {
		final var subscriptions = ((ExternalTaskClientImpl) client).getTopicSubscriptionManager().getSubscriptions();

		assertThat(subscriptions).extracting(TopicSubscription::getTopicName).containsExactlyInAnyOrder(
			"CancelProcessTask",
			"CheckDecisionTask",
			"CompleteProcessTask",
			"CreateAssetTask",
			"CreateDecisionTask",
			"NotifyCustomerTask",
			"ReconcileProcessesTask",
			"UpdateAssetTask");
		assertThat(subscriptions).allSatisfy(subscription -> assertThat(subscription.getTenantIdIn())
			.as("tenant filter for topic '%s'", subscription.getTopicName())
			.isEqualTo(of(TENANT_ID_ALKT)));
	}
}
