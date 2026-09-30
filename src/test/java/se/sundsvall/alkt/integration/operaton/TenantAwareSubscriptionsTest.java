package se.sundsvall.alkt.integration.operaton;

import java.util.List;
import org.camunda.bpm.client.spring.SpringTopicSubscription;
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
	private List<SpringTopicSubscription> subscriptions;

	@Test
	void everySubscriptionIsScopedToTheTenant() {
		assertThat(subscriptions).extracting(SpringTopicSubscription::getTopicName).containsExactlyInAnyOrder(
			"CancelProcessTask",
			"CheckDecisionTask",
			"CompleteProcessTask",
			"CreateAssetTask",
			"CreateDecisionTask",
			"NotifyCustomerTask",
			"ReconcileProcessesTask");
		assertThat(subscriptions).allSatisfy(subscription -> assertThat(subscription.getTenantIdIn())
			.as("tenant filter for topic '%s'", subscription.getTopicName())
			.isEqualTo(of(TENANT_ID_ALKT)));
	}
}
