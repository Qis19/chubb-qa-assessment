package com.example.demo.domain.claim;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Claim domain entity - validation rules")
class ClaimTest {

    private static final UUID CLAIM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDate PAST_DATE = LocalDate.now().minusDays(1);
    private static final String VALID_LOCATION = "Kuala Lumpur, Malaysia";
    private static final String VALID_DESCRIPTION = "Car accident on the highway during morning commute";
    private static final BigDecimal VALID_AMOUNT = new BigDecimal("5000");

    private Claim validClaim() {
        return new Claim(CLAIM_ID, USER_ID, PAST_DATE, VALID_LOCATION, VALID_DESCRIPTION, VALID_AMOUNT);
    }

    // -------------------- NULL ARGUMENTS --------------------

    @Test
    void nullClaimIdThrows() {
        assertThrows(NullPointerException.class,
            () -> new Claim(null, USER_ID, PAST_DATE, VALID_LOCATION, VALID_DESCRIPTION, VALID_AMOUNT));
    }

    @Test
    void nullUserIdThrows() {
        assertThrows(NullPointerException.class,
            () -> new Claim(CLAIM_ID, null, PAST_DATE, VALID_LOCATION, VALID_DESCRIPTION, VALID_AMOUNT));
    }

    @Test
    void nullIncidentDateThrows() {
        assertThrows(NullPointerException.class,
            () -> new Claim(CLAIM_ID, USER_ID, null, VALID_LOCATION, VALID_DESCRIPTION, VALID_AMOUNT));
    }

    @Test
    void nullDescriptionThrows() {
        assertThrows(NullPointerException.class,
            () -> new Claim(CLAIM_ID, USER_ID, PAST_DATE, VALID_LOCATION, null, VALID_AMOUNT));
    }

    @Test
    void nullAmountThrows() {
        assertThrows(NullPointerException.class,
            () -> new Claim(CLAIM_ID, USER_ID, PAST_DATE, VALID_LOCATION, VALID_DESCRIPTION, null));
    }

    @Test
    void nullLocationThrows() {
        assertThrows(NullPointerException.class,
            () -> new Claim(CLAIM_ID, USER_ID, PAST_DATE, null, VALID_DESCRIPTION, VALID_AMOUNT));
    }

    // -------------------- INCIDENT DATE --------------------

    @Test
    void futureIncidentDateThrows() {
        LocalDate future = LocalDate.now().plusDays(1);
        assertThrows(InvalidClaimException.class,
            () -> new Claim(CLAIM_ID, USER_ID, future, VALID_LOCATION, VALID_DESCRIPTION, VALID_AMOUNT));
    }

    @Test
    void todayIncidentDateAllowed() {
        LocalDate today = LocalDate.now();
        assertDoesNotThrow(
            () -> new Claim(CLAIM_ID, USER_ID, today, VALID_LOCATION, VALID_DESCRIPTION, VALID_AMOUNT));
    }

    // -------------------- DESCRIPTION --------------------

    @Test
    void descriptionShorterThan10CharsThrows() {
        assertThrows(InvalidClaimException.class,
            () -> new Claim(CLAIM_ID, USER_ID, PAST_DATE, VALID_LOCATION, "Short", VALID_AMOUNT));
    }

    @Test
    void descriptionExactly10CharsAllowed() {
        String desc = "1234567890";
        assertDoesNotThrow(
            () -> new Claim(CLAIM_ID, USER_ID, PAST_DATE, VALID_LOCATION, desc, VALID_AMOUNT));
    }

    @Test
    void descriptionExactly1000CharsAllowed() {
        String desc = "a".repeat(1000);
        assertDoesNotThrow(
            () -> new Claim(CLAIM_ID, USER_ID, PAST_DATE, VALID_LOCATION, desc, VALID_AMOUNT));
    }

    @Test
    void description1001CharsThrows() {
        String desc = "a".repeat(1001);
        assertThrows(InvalidClaimException.class,
            () -> new Claim(CLAIM_ID, USER_ID, PAST_DATE, VALID_LOCATION, desc, VALID_AMOUNT));
    }

    @Test
    void whitespaceOnlyDescriptionThrows() {
        assertThrows(InvalidClaimException.class,
            () -> new Claim(CLAIM_ID, USER_ID, PAST_DATE, VALID_LOCATION, "          ", VALID_AMOUNT));
    }

    // -------------------- AMOUNT --------------------

    @Test
    void zeroAmountThrows() {
        assertThrows(InvalidClaimException.class,
            () -> new Claim(CLAIM_ID, USER_ID, PAST_DATE, VALID_LOCATION, VALID_DESCRIPTION, BigDecimal.ZERO));
    }

    @Test
    void negativeAmountThrows() {
        assertThrows(InvalidClaimException.class,
            () -> new Claim(CLAIM_ID, USER_ID, PAST_DATE, VALID_LOCATION, VALID_DESCRIPTION, new BigDecimal("-1")));
    }

    @Test
    void oneCentAmountAllowed() {
        assertDoesNotThrow(
            () -> new Claim(CLAIM_ID, USER_ID, PAST_DATE, VALID_LOCATION, VALID_DESCRIPTION, new BigDecimal("0.01")));
    }

    @Test
    void maxAmountAllowed() {
        assertDoesNotThrow(
            () -> new Claim(CLAIM_ID, USER_ID, PAST_DATE, VALID_LOCATION, VALID_DESCRIPTION, new BigDecimal("1000000")));
    }

    @Test
    void overMaxAmountThrows() {
        assertThrows(InvalidClaimException.class,
            () -> new Claim(CLAIM_ID, USER_ID, PAST_DATE, VALID_LOCATION, VALID_DESCRIPTION, new BigDecimal("1000000.01")));
    }

    // -------------------- LOCATION --------------------

    @Test
    void locationShorterThan5CharsThrows() {
        assertThrows(InvalidClaimException.class,
            () -> new Claim(CLAIM_ID, USER_ID, PAST_DATE, "ABCD", VALID_DESCRIPTION, VALID_AMOUNT));
    }

    @Test
    void locationExactly5CharsAllowed() {
        assertDoesNotThrow(
            () -> new Claim(CLAIM_ID, USER_ID, PAST_DATE, "ABCDE", VALID_DESCRIPTION, VALID_AMOUNT));
    }

    @Test
    void locationOver200CharsThrows() {
        String location = "a".repeat(201);
        assertThrows(InvalidClaimException.class,
            () -> new Claim(CLAIM_ID, USER_ID, PAST_DATE, location, VALID_DESCRIPTION, VALID_AMOUNT));
    }

    // -------------------- CREATION BEHAVIOR --------------------

    @Test
    void validClaimStartsWithSubmittedStatus() {
        assertEquals(ClaimStatus.SUBMITTED, validClaim().getStatus());
    }

    @Test
    void validClaimEmitsSubmittedEvent() {
        Claim claim = validClaim();
        assertEquals(1, claim.getDomainEvents().size());
    }

    @Test
    void createdAtIsSetOnCreation() {
        assertNotNull(validClaim().getCreatedAt());
    }

    // -------------------- BUG: changedBy --------------------

    @Test
    @DisplayName("BUG: changedBy on status change is the claim owner, not the admin")
    void changedByIsClaimOwnerNotAdmin_bug() {
        Claim claim = validClaim();
        claim.clearEvents();

        claim.updateStatus(ClaimStatus.UNDER_REVIEW);

        var event = (com.example.demo.domain.claim.events.ClaimStatusChanged) claim.getDomainEvents().get(0);

        // BUG: changedBy = claim owner (userId), NOT the acting admin
        assertEquals(USER_ID, event.changedBy(),
            "BUG: expected claim owner's id (documenting current behavior)");
    }

    // -------------------- getDomainEvents immutability --------------------

    @Test
    void getDomainEventsReturnsImmutableList() {
        Claim claim = validClaim();
        assertThrows(UnsupportedOperationException.class,
            () -> claim.getDomainEvents().clear());
    }
}