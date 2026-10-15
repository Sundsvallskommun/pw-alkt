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
	void aPermitHasACertificateTemplate(final AssetType assetType) {
		assertThat(assetType.getCertificateTemplate()).isPresent();
	}

	@ParameterizedTest
	@EnumSource(mode = MATCH_ALL, names = ".*_NOTIFICATION")
	void aNotificationHasNoCertificateTemplate(final AssetType assetType) {
		assertThat(assetType.getCertificateTemplate()).isEmpty();
	}
}
