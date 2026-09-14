package com.example.demo.domain.claim;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ClaimStatus transition rules")
class ClaimStatusTest {

    @ParameterizedTest(name = "{0} -> {1} should be ALLOWED")
    @CsvSource({
        "SUBMITTED,    UNDER_REVIEW",
        "SUBMITTED,    REJECTED",
        "UNDER_REVIEW, APPROVED",
        "UNDER_REVIEW, REJECTED",
        "UNDER_REVIEW, SUBMITTED",
        "APPROVED,     CLOSED",
        "REJECTED,     CLOSED"
    })
    void validTransitionsAreAllowed(ClaimStatus from, ClaimStatus to) {
        assertTrue(from.canTransitionTo(to),
            () -> String.format("Expected %s -> %s to be ALLOWED", from, to));
    }

    @ParameterizedTest(name = "{0} -> {1} should be BLOCKED")
    @CsvSource({
        "SUBMITTED,    APPROVED",
        "SUBMITTED,    CLOSED",
        "SUBMITTED,    SUBMITTED",
        "UNDER_REVIEW, CLOSED",
        "UNDER_REVIEW, UNDER_REVIEW",
        "APPROVED,     SUBMITTED",
        "APPROVED,     UNDER_REVIEW",
        "APPROVED,     REJECTED",
        "APPROVED,     APPROVED",
        "REJECTED,     SUBMITTED",
        "REJECTED,     UNDER_REVIEW",
        "REJECTED,     APPROVED",
        "REJECTED,     REJECTED",
        "CLOSED,       SUBMITTED",
        "CLOSED,       UNDER_REVIEW",
        "CLOSED,       APPROVED",
        "CLOSED,       REJECTED",
        "CLOSED,       CLOSED"
    })
    void invalidTransitionsAreBlocked(ClaimStatus from, ClaimStatus to) {
        assertFalse(from.canTransitionTo(to),
            () -> String.format("Expected %s -> %s to be BLOCKED", from, to));
    }

    @ParameterizedTest(name = "CLOSED is terminal - cannot transition to {0}")
    @CsvSource({"SUBMITTED","UNDER_REVIEW","APPROVED","REJECTED","CLOSED"})
    void closedIsTerminal(ClaimStatus target) {
        assertFalse(ClaimStatus.CLOSED.canTransitionTo(target));
    }

    @ParameterizedTest(name = "{0} cannot transition to itself")
    @CsvSource({"SUBMITTED","UNDER_REVIEW","APPROVED","REJECTED","CLOSED"})
    void noSelfTransition(ClaimStatus status) {
        assertFalse(status.canTransitionTo(status),
            () -> String.format("%s should not transition to itself", status));
    }
}