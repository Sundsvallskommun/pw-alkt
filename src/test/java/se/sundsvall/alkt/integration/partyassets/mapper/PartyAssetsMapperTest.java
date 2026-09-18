package se.sundsvall.alkt.integration.partyassets.mapper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.alkt.integration.partyassets.mapper.PartyAssetsMapper.toAssetCreateRequest;

class PartyAssetsMapperTest {

	/** A placeholder until the worker knows what it builds the request from. Nothing to assert on the content yet. */
	@Test
	void toAssetCreateRequestAnswersWithARequest() {
		assertThat(toAssetCreateRequest()).isNotNull();
	}
}
