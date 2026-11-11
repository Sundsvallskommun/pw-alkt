package se.sundsvall.alkt.service.model;

public record CertificateDelivery(Channel channel, String messageId, boolean alreadySent) {

	public enum Channel {
		DIGITAL_MAIL,
		SNAIL_MAIL
	}
}
