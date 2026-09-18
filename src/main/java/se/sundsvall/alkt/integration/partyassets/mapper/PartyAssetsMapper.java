package se.sundsvall.alkt.integration.partyassets.mapper;

import generated.se.sundsvall.partyassets.AssetCreateRequest;

public final class PartyAssetsMapper {

	private PartyAssetsMapper() {}

	// TODO: build the request from the Support Management errand, the restaurant number from licensed-business and the
	// partyId. Which errand fields and parameters it reads is not decided yet, so the arguments come with the worker.
	public static AssetCreateRequest toAssetCreateRequest() {
		return new AssetCreateRequest();
	}
}
