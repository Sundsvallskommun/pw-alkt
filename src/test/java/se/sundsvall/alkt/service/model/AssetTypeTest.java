package se.sundsvall.alkt.service.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.EnumSource.Mode.MATCH_ALL;
import static se.sundsvall.alkt.service.model.AssetType.ALCOHOL_SERVING_PERMIT;
import static se.sundsvall.alkt.service.model.AssetType.TOBACCO_SALES_PERMIT;

class AssetTypeTest {

	@Test
	void eachPermitHasItsOwnCertificateTemplate() {
		assertThat(ALCOHOL_SERVING_PERMIT.getCertificateTemplate()).contains("serving-permit-certificate");
		assertThat(TOBACCO_SALES_PERMIT.getCertificateTemplate()).contains("tobacco-permit-certificate");
	}

	@ParameterizedTest
	@EnumSource(mode = MATCH_ALL, names = ".*_PERMIT")
	void aPermitIsAPermitWithACertificateTemplate(final AssetType assetType) {
		assertThat(assetType.isPermit()).isTrue();
		assertThat(assetType.getCertificateTemplate()).isPresent();
	}

	@ParameterizedTest
	@EnumSource(mode = MATCH_ALL, names = ".*_NOTIFICATION")
	void aNotificationIsNoPermitAndHasNoCertificateTemplate(final AssetType assetType) {
		assertThat(assetType.isPermit()).isFalse();
		assertThat(assetType.getCertificateTemplate()).isEmpty();
	}

	@ParameterizedTest
	@EnumSource(names = {
		"ALCOHOL_SERVING_PERMIT", "LOW_ALCOHOL_BEER_SALES_NOTIFICATION", "LOW_ALCOHOL_BEER_SERVING_NOTIFICATION", "LOW_ALCOHOL_BEER_SALES_AND_SERVING_NOTIFICATION",
		"CATERING_OCCASION_NOTIFICATION"
	})
	void anAlcoholAssetTypeFollowsTheAlcoholSchema(final AssetType assetType) {
		assertThat(assetType.getSchema()).isEqualTo(PermitSchema.ALCOHOL);
	}

	@ParameterizedTest
	@EnumSource(names = {
		"TOBACCO_SALES_PERMIT", "E_CIGARETTE_SALES_NOTIFICATION", "TOBACCO_FREE_NICOTINE_PRODUCT_SALES_NOTIFICATION"
	})
	void aTobaccoAssetTypeFollowsTheTobaccoSchema(final AssetType assetType) {
		assertThat(assetType.getSchema()).isEqualTo(PermitSchema.TOBACCO);
	}
}
