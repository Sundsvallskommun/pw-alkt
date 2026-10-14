package se.sundsvall.alkt.service.model;

/** The asset types this service creates in party-assets. */
public enum AssetType {
	ALCOHOL_SERVING_PERMIT("AlcoholServingPermit"),
	TOBACCO_SALES_PERMIT("TobaccoSalesPermit"),
	LOW_ALCOHOL_BEER_SALES_NOTIFICATION("LowAlcoholBeerSalesNotification"),
	LOW_ALCOHOL_BEER_SERVING_NOTIFICATION("LowAlcoholBeerServingNotification"),
	LOW_ALCOHOL_BEER_SALES_AND_SERVING_NOTIFICATION("LowAlcoholBeerSalesAndServingNotification"),
	E_CIGARETTE_SALES_NOTIFICATION("ECigaretteSalesNotification"),
	TOBACCO_FREE_NICOTINE_PRODUCT_SALES_NOTIFICATION("TobaccoFreeNicotineProductSalesNotification"),
	CATERING_OCCASION_NOTIFICATION("CateringOccasionNotification");

	private final String value;

	AssetType(final String value) {
		this.value = value;
	}

	/** The type as party-assets knows it. */
	public String value() {
		return value;
	}
}
