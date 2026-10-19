package se.sundsvall.alkt.service.model;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import se.sundsvall.alkt.exception.NonRetryableException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static se.sundsvall.alkt.service.model.AssetType.ALCOHOL_SERVING_PERMIT;
import static se.sundsvall.alkt.service.model.AssetType.CATERING_OCCASION_NOTIFICATION;
import static se.sundsvall.alkt.service.model.AssetType.E_CIGARETTE_SALES_NOTIFICATION;
import static se.sundsvall.alkt.service.model.AssetType.LOW_ALCOHOL_BEER_SALES_AND_SERVING_NOTIFICATION;
import static se.sundsvall.alkt.service.model.AssetType.LOW_ALCOHOL_BEER_SALES_NOTIFICATION;
import static se.sundsvall.alkt.service.model.AssetType.LOW_ALCOHOL_BEER_SERVING_NOTIFICATION;
import static se.sundsvall.alkt.service.model.AssetType.TOBACCO_FREE_NICOTINE_PRODUCT_SALES_NOTIFICATION;
import static se.sundsvall.alkt.service.model.AssetType.TOBACCO_SALES_PERMIT;
import static se.sundsvall.alkt.service.model.ProcessType.ALCOHOL_SERVING;
import static se.sundsvall.alkt.service.model.ProcessType.ALCOHOL_SERVING_ADDITION;
import static se.sundsvall.alkt.service.model.ProcessType.ALCOHOL_SERVING_CHANGE;
import static se.sundsvall.alkt.service.model.ProcessType.CATERING_OCCASION;
import static se.sundsvall.alkt.service.model.ProcessType.EXTERNAL_INSPECTION;
import static se.sundsvall.alkt.service.model.ProcessType.E_CIGARETTE_SALES;
import static se.sundsvall.alkt.service.model.ProcessType.LOW_ALCOHOL_BEER_SALES;
import static se.sundsvall.alkt.service.model.ProcessType.LOW_ALCOHOL_BEER_SALES_AND_SERVING;
import static se.sundsvall.alkt.service.model.ProcessType.LOW_ALCOHOL_BEER_SERVING;
import static se.sundsvall.alkt.service.model.ProcessType.TOBACCO_FREE_NICOTINE_PRODUCT_SALES;
import static se.sundsvall.alkt.service.model.ProcessType.TOBACCO_SALES;
import static se.sundsvall.alkt.service.model.ProcessType.TOBACCO_SALES_CHANGE;
import static se.sundsvall.alkt.service.model.ProcessType.TOBACCO_SALES_CLOSURE;

class ProcessTypeTest {

	@Test
	void fromKey() {
		Arrays.stream(ProcessType.values()).forEach(process -> assertThat(ProcessType.fromKey(process.getKey())).contains(process));
	}

	@Test
	void fromKeyIsEmptyForAnUnknownKey() {
		assertThat(ProcessType.fromKey("process-reconciliation")).isEmpty();
		assertThat(ProcessType.fromKey(null)).isEmpty();
	}

	@Test
	void ofKey() {
		assertThat(ProcessType.ofKey("alcohol-serving")).isEqualTo(ALCOHOL_SERVING);
	}

	@Test
	void ofKeyRefusesAnUnknownKey() {
		assertThatThrownBy(() -> ProcessType.ofKey("process-reconciliation"))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Process 'process-reconciliation' is not a process of this service");
		assertThatThrownBy(() -> ProcessType.ofKey(null))
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Process 'null' is not a process of this service");
	}

	@Test
	void keys() {
		assertThat(ProcessType.keys())
			.hasSize(ProcessType.values().length)
			.contains(ALCOHOL_SERVING.getKey());
	}

	/** An addition is a serving permit of its own, which alcohol-serving-change can change like the main one. */
	@Test
	void getAssetTypeGivesTheServingProcessesOneType() {
		assertThat(ALCOHOL_SERVING.getAssetType()).isEqualTo(ALCOHOL_SERVING_PERMIT);
		assertThat(ALCOHOL_SERVING_CHANGE.getAssetType()).isEqualTo(ALCOHOL_SERVING_PERMIT);
		assertThat(ALCOHOL_SERVING_ADDITION.getAssetType()).isEqualTo(ALCOHOL_SERVING_PERMIT);
	}

	@Test
	void getAssetTypeGivesTheTobaccoProcessesOneType() {
		assertThat(TOBACCO_SALES.getAssetType()).isEqualTo(TOBACCO_SALES_PERMIT);
		assertThat(TOBACCO_SALES_CHANGE.getAssetType()).isEqualTo(TOBACCO_SALES_PERMIT);
		assertThat(TOBACCO_SALES_CLOSURE.getAssetType()).isEqualTo(TOBACCO_SALES_PERMIT);
	}

	@Test
	void getAssetTypeGivesEachNotificationProcessATypeOfItsOwn() {
		assertThat(LOW_ALCOHOL_BEER_SALES.getAssetType()).isEqualTo(LOW_ALCOHOL_BEER_SALES_NOTIFICATION);
		assertThat(LOW_ALCOHOL_BEER_SERVING.getAssetType()).isEqualTo(LOW_ALCOHOL_BEER_SERVING_NOTIFICATION);
		assertThat(LOW_ALCOHOL_BEER_SALES_AND_SERVING.getAssetType()).isEqualTo(LOW_ALCOHOL_BEER_SALES_AND_SERVING_NOTIFICATION);
		assertThat(E_CIGARETTE_SALES.getAssetType()).isEqualTo(E_CIGARETTE_SALES_NOTIFICATION);
		assertThat(TOBACCO_FREE_NICOTINE_PRODUCT_SALES.getAssetType()).isEqualTo(TOBACCO_FREE_NICOTINE_PRODUCT_SALES_NOTIFICATION);
		assertThat(CATERING_OCCASION.getAssetType()).isEqualTo(CATERING_OCCASION_NOTIFICATION);
	}

	@Test
	void getAssetTypeRefusesAProcessWithoutAnAssetType() {
		assertThatThrownBy(EXTERNAL_INSPECTION::getAssetType)
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Process 'external-inspection' has no asset type");
	}

	@Test
	void getDecisionTitleNamesThePermitOfEachNotificationProcess() {
		assertThat(LOW_ALCOHOL_BEER_SALES.getDecisionTitle()).isEqualTo("Anmälan om försäljning av folköl");
		assertThat(LOW_ALCOHOL_BEER_SERVING.getDecisionTitle()).isEqualTo("Anmälan om servering av folköl");
		assertThat(LOW_ALCOHOL_BEER_SALES_AND_SERVING.getDecisionTitle()).isEqualTo("Anmälan om försäljning och servering av folköl");
		assertThat(E_CIGARETTE_SALES.getDecisionTitle()).isEqualTo("Anmälan om försäljning av elektroniska cigaretter och påfyllningsbehållare");
		assertThat(TOBACCO_FREE_NICOTINE_PRODUCT_SALES.getDecisionTitle()).isEqualTo("Anmälan om försäljning av tobaksfria nikotinprodukter");
		assertThat(CATERING_OCCASION.getDecisionTitle()).isEqualTo("Anmälan om cateringtillfälle");
	}

	@Test
	void getDecisionTitleRefusesAProcessWithoutAnAutomaticDecision() {
		assertThatThrownBy(ALCOHOL_SERVING::getDecisionTitle)
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Process 'alcohol-serving' has no title for a decision made automatically");
	}

	@Test
	void getChangeDraftTitleNamesThePermitOfEachChangeProcess() {
		assertThat(ALCOHOL_SERVING_CHANGE.getChangeDraftTitle()).isEqualTo("Ändring av serveringstillstånd");
		assertThat(TOBACCO_SALES_CHANGE.getChangeDraftTitle()).isEqualTo("Ändring av tobakstillstånd");
	}

	@Test
	void getChangeDraftTitleRefusesAProcessWithoutAChangeDraft() {
		assertThatThrownBy(ALCOHOL_SERVING::getChangeDraftTitle)
			.isInstanceOf(NonRetryableException.class)
			.hasMessage("Process 'alcohol-serving' has no title for a change draft");
	}

	@ParameterizedTest
	@CsvSource({
		"ALCOHOL_SERVING, APPROVAL", "ALCOHOL_SERVING, APPROVAL_WITH_CONDITIONS", "ALCOHOL_SERVING, REJECTED", "ALCOHOL_SERVING, DISMISSED",
		"ALCOHOL_SERVING, INADMISSIBLE", "ALCOHOL_SERVING, NONE", "ACTION_ERRAND, REPRIMAND", "ACTION_ERRAND, WARNING", "ACTION_ERRAND, REVOCATION",
		"ACTION_ERRAND, NONE"
	})
	void isKnownOutcomeKnowsTheOutcomesOfTheProcess(final ProcessType processType, final String outcome) {
		assertThat(processType.isKnownOutcome(outcome)).isTrue();
	}

	/** An outcome outside the set of the process is not known, a near miss of a known one included. */
	@ParameterizedTest
	@CsvSource({
		"ALCOHOL_SERVING, WARNING", "ALCOHOL_SERVING, REJECTION", "ALCOHOL_SERVING, PARTIAL_APPROVAL", "ALCOHOL_SERVING, approval", "ALCOHOL_SERVING, null",
		"ACTION_ERRAND, APPROVAL", "ACTION_ERRAND, null"
	})
	void isKnownOutcomeDoesNotKnowTheOutcomesOfAnotherProcess(final ProcessType processType, final String outcome) {
		assertThat(processType.isKnownOutcome(outcome)).isFalse();
	}
}
