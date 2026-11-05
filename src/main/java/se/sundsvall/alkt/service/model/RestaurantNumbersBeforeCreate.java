package se.sundsvall.alkt.service.model;

import java.util.List;

/**
 * The free restaurant numbers at an address just before a number is created there, by which a rerun at the same address
 * finds the number an earlier run created.
 */
public record RestaurantNumbersBeforeCreate(String addressId, List<String> numbers) {
}
