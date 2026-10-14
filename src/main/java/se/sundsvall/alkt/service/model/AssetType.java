package se.sundsvall.alkt.service.model;

import java.util.Optional;

/** The asset types this service creates in party-assets. */
public enum AssetType {
	ALCOHOL_SERVING_PERMIT("AlcoholServingPermit", "serving-permit-certificate"),
	TOBACCO_SALES_PERMIT("TobaccoSalesPermit", "tobacco-permit-certificate"),
	LOW_ALCOHOL_BEER_SALES_NOTIFICATION("LowAlcoholBeerSalesNotification", null),
	LOW_ALCOHOL_BEER_SERVING_NOTIFICATION("LowAlcoholBeerServingNotification", null),
	LOW_ALCOHOL_BEER_SALES_AND_SERVING_NOTIFICATION("LowAlcoholBeerSalesAndServingNotification", null),
	E_CIGARETTE_SALES_NOTIFICATION("ECigaretteSalesNotification", null),
	TOBACCO_FREE_NICOTINE_PRODUCT_SALES_NOTIFICATION("TobaccoFreeNicotineProductSalesNotification", null),
	CATERING_OCCASION_NOTIFICATION("CateringOccasionNotification", null);

	private final String value;
	private final String certificateTemplate;

	AssetType(final String value, final String certificateTemplate) {
		this.value = value;
		this.certificateTemplate = certificateTemplate;
	}

	/** The type as party-assets knows it. */
	public String value() {
		return value;
	}

	/** The template in templating that renders the certificate. A notification has none. */
	public Optional<String> certificateTemplate() {
		return Optional.ofNullable(certificateTemplate);
	}
}
