package se.sundsvall.alkt.service.model;

/** The chosen number, its address and the id of its latest assignment at the time, empty when it had none. */
public record ResolvedRestaurantNumber(String number, String addressId, String latestAssignmentId) {
}
