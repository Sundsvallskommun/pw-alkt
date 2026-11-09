package se.sundsvall.alkt.service.model;

import java.util.Optional;

import static se.sundsvall.alkt.service.model.PermitSchema.ALCOHOL;
import static se.sundsvall.alkt.service.model.PermitSchema.TOBACCO;

/** The asset types this service creates in party-assets. */
public enum AssetType {
	ALCOHOL_SERVING_PERMIT("AlcoholServingPermit", "serving-permit-certificate", ALCOHOL),
	TOBACCO_SALES_PERMIT("TobaccoSalesPermit", "tobacco-permit-certificate", TOBACCO),
	LOW_ALCOHOL_BEER_SALES_NOTIFICATION("LowAlcoholBeerSalesNotification", null, ALCOHOL),
	LOW_ALCOHOL_BEER_SERVING_NOTIFICATION("LowAlcoholBeerServingNotification", null, ALCOHOL),
	LOW_ALCOHOL_BEER_SALES_AND_SERVING_NOTIFICATION("LowAlcoholBeerSalesAndServingNotification", null, ALCOHOL),
	E_CIGARETTE_SALES_NOTIFICATION("ECigaretteSalesNotification", null, TOBACCO),
	TOBACCO_FREE_NICOTINE_PRODUCT_SALES_NOTIFICATION("TobaccoFreeNicotineProductSalesNotification", null, TOBACCO),
	CATERING_OCCASION_NOTIFICATION("CateringOccasionNotification", null, ALCOHOL);

	private final String value;
	private final String certificateTemplate;
	private final PermitSchema schema;

	AssetType(final String value, final String certificateTemplate, final PermitSchema schema) {
		this.value = value;
		this.certificateTemplate = certificateTemplate;
		this.schema = schema;
	}

	/** The type as party-assets knows it. */
	public String getValue() {
		return value;
	}

	/** The template in templating that renders the certificate. A notification has none. */
	public Optional<String> getCertificateTemplate() {
		return Optional.ofNullable(certificateTemplate);
	}

	public PermitSchema getSchema() {
		return schema;
	}

	/** A permit is granted from the JSON parameter of its decision and has a certificate; a notification from its form. */
	public boolean isPermit() {
		return certificateTemplate != null;
	}
}
