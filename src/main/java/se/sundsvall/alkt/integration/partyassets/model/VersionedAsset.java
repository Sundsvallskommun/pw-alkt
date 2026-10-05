package se.sundsvall.alkt.integration.partyassets.model;

import generated.se.sundsvall.partyassets.Asset;

/** The version is the ETag of the read, sent back as If-Match so an update cannot overwrite a change made since. */
public record VersionedAsset(Asset asset, String version) {
}
