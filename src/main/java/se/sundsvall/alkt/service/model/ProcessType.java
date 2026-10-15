package se.sundsvall.alkt.service.model;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toUnmodifiableSet;
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
	ACTION_ERRAND("action-errand", null, null, null);

	private final String key;
	private final AssetType assetType;
	private final String decisionTitle;
	private final String changeDraftTitle;

	ProcessType(final String key, final AssetType assetType, final String decisionTitle, final String changeDraftTitle) {
		this.key = key;
		this.assetType = assetType;
		this.decisionTitle = decisionTitle;
		this.changeDraftTitle = changeDraftTitle;
	}

	public static Optional<ProcessType> fromKey(final String key) {
		return Arrays.stream(values())
			.filter(process -> process.key.equals(key))
			.findFirst();
	}

	public static Set<String> keys() {
		return keysWhere(_ -> true);
	}

	public static Set<String> keysWhere(final Predicate<ProcessType> condition) {
		return Arrays.stream(values())
			.filter(condition)
			.map(ProcessType::getKey)
			.collect(toUnmodifiableSet());
	}

	public String getKey() {
		return key;
	}

	public Optional<AssetType> getAssetType() {
		return Optional.ofNullable(assetType);
	}

	/** The title of a decision the process makes automatically. */
	public Optional<String> getDecisionTitle() {
		return Optional.ofNullable(decisionTitle);
	}

	public Optional<String> getChangeDraftTitle() {
		return Optional.ofNullable(changeDraftTitle);
	}
}
