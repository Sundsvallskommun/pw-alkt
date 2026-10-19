package se.sundsvall.alkt.service.model;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import se.sundsvall.alkt.exception.NonRetryableException;

import static java.util.stream.Collectors.toUnmodifiableSet;
import static se.sundsvall.alkt.Constants.ACTION_ERRAND_DECISION_OUTCOMES;
import static se.sundsvall.alkt.Constants.DECISION_OUTCOMES;
import static se.sundsvall.alkt.Constants.DECISION_OUTCOME_NONE;
import static se.sundsvall.alkt.service.model.AssetType.ALCOHOL_SERVING_PERMIT;
import static se.sundsvall.alkt.service.model.AssetType.CATERING_OCCASION_NOTIFICATION;
import static se.sundsvall.alkt.service.model.AssetType.E_CIGARETTE_SALES_NOTIFICATION;
import static se.sundsvall.alkt.service.model.AssetType.LOW_ALCOHOL_BEER_SALES_AND_SERVING_NOTIFICATION;
import static se.sundsvall.alkt.service.model.AssetType.LOW_ALCOHOL_BEER_SALES_NOTIFICATION;
import static se.sundsvall.alkt.service.model.AssetType.LOW_ALCOHOL_BEER_SERVING_NOTIFICATION;
import static se.sundsvall.alkt.service.model.AssetType.TOBACCO_FREE_NICOTINE_PRODUCT_SALES_NOTIFICATION;
import static se.sundsvall.alkt.service.model.AssetType.TOBACCO_SALES_PERMIT;

/** The processes an errand can start. Each key must match the id of the process in its bpmn schema. */
public enum ProcessType {

	ALCOHOL_SERVING("alcohol-serving", ALCOHOL_SERVING_PERMIT, null, null),
	ALCOHOL_SERVING_CHANGE("alcohol-serving-change", ALCOHOL_SERVING_PERMIT, null, "Ändring av serveringstillstånd"),
	ALCOHOL_SERVING_ADDITION("alcohol-serving-addition", ALCOHOL_SERVING_PERMIT, null, null),
	TOBACCO_SALES("tobacco-sales", TOBACCO_SALES_PERMIT, null, null),
	TOBACCO_SALES_CHANGE("tobacco-sales-change", TOBACCO_SALES_PERMIT, null, "Ändring av tobakstillstånd"),
	TOBACCO_SALES_CLOSURE("tobacco-sales-closure", TOBACCO_SALES_PERMIT, null, null),
	E_CIGARETTE_SALES("e-cigarette-sales", E_CIGARETTE_SALES_NOTIFICATION, "Anmälan om försäljning av elektroniska cigaretter och påfyllningsbehållare", null),
	TOBACCO_FREE_NICOTINE_PRODUCT_SALES("tobacco-free-nicotine-product-sales", TOBACCO_FREE_NICOTINE_PRODUCT_SALES_NOTIFICATION, "Anmälan om försäljning av tobaksfria nikotinprodukter", null),
	LOW_ALCOHOL_BEER_SERVING("low-alcohol-beer-serving", LOW_ALCOHOL_BEER_SERVING_NOTIFICATION, "Anmälan om servering av folköl", null),
	LOW_ALCOHOL_BEER_SALES("low-alcohol-beer-sales", LOW_ALCOHOL_BEER_SALES_NOTIFICATION, "Anmälan om försäljning av folköl", null),
	LOW_ALCOHOL_BEER_SALES_AND_SERVING("low-alcohol-beer-sales-and-serving", LOW_ALCOHOL_BEER_SALES_AND_SERVING_NOTIFICATION, "Anmälan om försäljning och servering av folköl", null),
	CATERING_OCCASION("catering-occasion", CATERING_OCCASION_NOTIFICATION, "Anmälan om cateringtillfälle", null),
	EXTERNAL_INSPECTION("external-inspection", null, null, null),
	INTERNAL_INSPECTION("internal-inspection", null, null, null),
	ACTION_ERRAND("action-errand", null, null, null, ACTION_ERRAND_DECISION_OUTCOMES);

	private final String key;
	private final AssetType assetType;
	private final String decisionTitle;
	private final String changeDraftTitle;
	private final Set<String> decisionOutcomes;

	ProcessType(final String key, final AssetType assetType, final String decisionTitle, final String changeDraftTitle) {
		this(key, assetType, decisionTitle, changeDraftTitle, DECISION_OUTCOMES);
	}

	ProcessType(final String key, final AssetType assetType, final String decisionTitle, final String changeDraftTitle, final Set<String> decisionOutcomes) {
		this.key = key;
		this.assetType = assetType;
		this.decisionTitle = decisionTitle;
		this.changeDraftTitle = changeDraftTitle;
		this.decisionOutcomes = decisionOutcomes;
	}

	public static Optional<ProcessType> fromKey(final String key) {
		return Arrays.stream(values())
			.filter(process -> process.key.equals(key))
			.findFirst();
	}

	/** The process of a running instance. A key no process has is a fault a retry cannot fix. */
	public static ProcessType ofKey(final String key) {
		return fromKey(key)
			.orElseThrow(() -> new NonRetryableException("Process '%s' is not a process of this service".formatted(key)));
	}

	public static Set<String> keys() {
		return Arrays.stream(values())
			.map(ProcessType::getKey)
			.collect(toUnmodifiableSet());
	}

	public String getKey() {
		return key;
	}

	public AssetType getAssetType() {
		return Optional.ofNullable(assetType)
			.orElseThrow(() -> new NonRetryableException("Process '%s' has no asset type".formatted(key)));
	}

	/** The title of a decision the process makes automatically. */
	public String getDecisionTitle() {
		return Optional.ofNullable(decisionTitle)
			.orElseThrow(() -> new NonRetryableException("Process '%s' has no title for a decision made automatically".formatted(key)));
	}

	public String getChangeDraftTitle() {
		return Optional.ofNullable(changeDraftTitle)
			.orElseThrow(() -> new NonRetryableException("Process '%s' has no title for a change draft".formatted(key)));
	}

	/** The outcomes of the decision gateway. NONE only keeps the process waiting, so every process knows it. */
	public boolean isKnownOutcome(final String outcome) {
		return DECISION_OUTCOME_NONE.equals(outcome) || decisionOutcomes.contains(outcome);
	}
}
