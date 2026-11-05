package se.sundsvall.alkt.service.model;

import java.util.Optional;

import static se.sundsvall.alkt.service.model.PermitSchema.ALCOHOL;
import static se.sundsvall.alkt.service.model.PermitSchema.TOBACCO;

/** The asset types this service creates in party-assets. */
public enum AssetType {
	ALCOHOL_SERVING_PERMIT("AlcoholServingPermit", "serving-permit-certificate", ALCOHOL),
	TOBACCO_SALES_PERMIT("TobaccoSalesPermit", "tobacco-permit-certificate", TOBACCO),
	LOW_ALCOHOL_BEER_SALES_NOTIFICATION("LowAlcoholBeerSalesNotification", null, null),
	LOW_ALCOHOL_BEER_SERVING_NOTIFICATION("LowAlcoholBeerServingNotification", null, null),
	LOW_ALCOHOL_BEER_SALES_AND_SERVING_NOTIFICATION("LowAlcoholBeerSalesAndServingNotification", null, null),
	E_CIGARETTE_SALES_NOTIFICATION("ECigaretteSalesNotification", null, null),
	TOBACCO_FREE_NICOTINE_PRODUCT_SALES_NOTIFICATION("TobaccoFreeNicotineProductSalesNotification", null, null),
	CATERING_OCCASION_NOTIFICATION("CateringOccasionNotification", null, null);

	private final String value;
	private final String certificateTemplate;
	private final PermitSchema permitSchema;

	AssetType(final String value, final String certificateTemplate, final PermitSchema permitSchema) {
		this.value = value;
		this.certificateTemplate = certificateTemplate;
		this.permitSchema = permitSchema;
	}

	/** The type as party-assets knows it. */
	public String getValue() {
		return value;
	}

	/** The template in templating that renders the certificate. A notification has none. */
	public Optional<String> getCertificateTemplate() {
		return Optional.ofNullable(certificateTemplate);
	}

	/** The schema the JSON parameter of the asset follows. A notification has none. */
	public Optional<PermitSchema> getPermitSchema() {
		return Optional.ofNullable(permitSchema);
	}
}
