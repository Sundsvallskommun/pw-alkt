package se.sundsvall.alkt.integration.partyassets.model;

import org.springframework.web.multipart.MultipartFile;

public record AssetFile(MultipartFile file, String category) {
}
