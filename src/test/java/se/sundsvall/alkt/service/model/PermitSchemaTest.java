package se.sundsvall.alkt.service.model;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.alkt.service.model.PermitSchema.ALCOHOL;
import static se.sundsvall.alkt.service.model.PermitSchema.TOBACCO;

class PermitSchemaTest {

	@Test
	void eachSchemaIsNamedAsInJsonSchema() {
		assertThat(ALCOHOL.getName()).isEqualTo("aot_alcohol_party_asset");
		assertThat(TOBACCO.getName()).isEqualTo("aot_tobacco_party_asset");
	}

	@Test
	void toSchemaIdPutsTheMunicipalityTheNameAndTheVersionTogether() {
		assertThat(ALCOHOL.toSchemaId("2281", "0.1")).isEqualTo("2281_aot_alcohol_party_asset_0.1");
		assertThat(TOBACCO.toSchemaId("2262", "1.0")).isEqualTo("2262_aot_tobacco_party_asset_1.0");
	}

	@Test
	void onlyTheAlcoholDecisionHasTheNewRestaurantNumber() {
		assertThat(ALCOHOL.getDecisionOnlyFields()).containsExactly(List.of("premises", "newRestaurantNumber"));
		assertThat(TOBACCO.getDecisionOnlyFields()).isEmpty();
	}

	@Test
	void onlyThePremisesOfTheAlcoholSchemaHasARestaurantNumber() {
		assertThat(ALCOHOL.getRestaurantNumberField()).contains(List.of("premises", "restaurantNumber"));
		assertThat(TOBACCO.getRestaurantNumberField()).isEmpty();
	}
}
