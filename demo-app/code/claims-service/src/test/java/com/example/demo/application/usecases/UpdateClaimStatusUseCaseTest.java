package com.example.demo.application.usecases;

import com.example.demo.domain.claim.Claim;
import com.example.demo.domain.claim.ClaimNotFoundException;
import com.example.demo.domain.claim.ClaimRepository;
import com.example.demo.domain.claim.ClaimStatus;
import com.example.demo.domain.claim.InvalidStatusTransitionException;
import com.example.demo.domain.claim.events.ClaimStatusChanged;
import com.example.demo.user.domain.Email;
import com.example.demo.user.domain.UnauthorizedException;
import com.example.demo.user.domain.User;
import com.example.demo.user.domain.UserId;
import com.example.demo.user.domain.UserRepository;
import com.example.demo.user.domain.UserRole;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("UpdateClaimStatusUseCase - RBAC, transitions, changedBy bug")
class UpdateClaimStatusUseCaseTest {

    private ClaimRepository claimRepository;
    private UserRepository userRepository;
    private ApplicationEventPublisher eventPublisher;
    private MeterRegistry meterRegistry;
    private UpdateClaimStatusUseCase useCase;

    private UUID adminId;
    private UUID claimantId;
    private UUID claimId;
    private User admin;
    private User claimant;
    private Claim claim;

    @BeforeEach
    void setUp() {
        claimRepository = mock(ClaimRepository.class);
        userRepository = mock(UserRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        meterRegistry = new SimpleMeterRegistry();

        useCase = new UpdateClaimStatusUseCase(
            claimRepository,
            userRepository,
            meterRegistry,
            eventPublisher
        );

        adminId = UUID.randomUUID();
        claimantId = UUID.randomUUID();
        claimId = UUID.randomUUID();

        admin = new User(
            UserId.of(adminId),
            "Admin User",
            Email.of("admin@demo.com"),
            UserRole.ADMIN,
            Instant.now()
        );

        claimant = new User(
            UserId.of(claimantId),
            "Claimant User",
            Email.of("claimant@demo.com"),
            UserRole.CLAIMANT,
            Instant.now()
        );

        claim = new Claim(
            claimId,
            claimantId,
            LocalDate.now().minusDays(1),
            "Kuala Lumpur, Malaysia",
            "Car accident on the highway during morning commute",
            new BigDecimal("5000")
        );
        claim.clearEvents();
    }

    @Test
    @DisplayName("unknown admin user is rejected")
    void unknownAdminUserIsRejected() {
        when(userRepository.findById(UserId.of(adminId))).thenReturn(Optional.empty());
        var command = new UpdateClaimStatusCommand(claimId, ClaimStatus.UNDER_REVIEW, adminId);
        assertThrows(UnauthorizedException.class, () -> useCase.execute(command));
    }

    @Test
    @DisplayName("non-admin user is rejected")
    void nonAdminUserIsRejected() {
        when(userRepository.findById(UserId.of(claimantId))).thenReturn(Optional.of(claimant));
        var command = new UpdateClaimStatusCommand(claimId, ClaimStatus.UNDER_REVIEW, claimantId);
        assertThrows(UnauthorizedException.class, () -> useCase.execute(command));
    }

    @Test
    @DisplayName("claim not found throws ClaimNotFoundException")
    void claimNotFoundThrows() {
        when(userRepository.findById(UserId.of(adminId))).thenReturn(Optional.of(admin));
        when(claimRepository.findById(claimId)).thenReturn(Optional.empty());
        var command = new UpdateClaimStatusCommand(claimId, ClaimStatus.UNDER_REVIEW, adminId);
        assertThrows(ClaimNotFoundException.class, () -> useCase.execute(command));
    }

    @Test
    @DisplayName("invalid transition is rejected")
    void invalidTransitionIsRejected() {
        when(userRepository.findById(UserId.of(adminId))).thenReturn(Optional.of(admin));
        when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));
        var command = new UpdateClaimStatusCommand(claimId, ClaimStatus.APPROVED, adminId);
        assertThrows(InvalidStatusTransitionException.class, () -> useCase.execute(command));
    }

    @Test
    @DisplayName("valid transition succeeds and returns updated claim")
    void validTransitionSucceeds() {
        when(userRepository.findById(UserId.of(adminId))).thenReturn(Optional.of(admin));
        when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(Claim.class))).thenAnswer(i -> i.getArgument(0));
        var command = new UpdateClaimStatusCommand(claimId, ClaimStatus.UNDER_REVIEW, adminId);
        Claim result = useCase.execute(command);
        assertEquals(ClaimStatus.UNDER_REVIEW, result.getStatus());
        verify(claimRepository).save(any(Claim.class));
    }

        @Test
    @DisplayName("BUG: changedBy is claim owner not admin")
    void changedByIsClaimOwnerNotAdmin_bug() {
        when(userRepository.findById(UserId.of(adminId))).thenReturn(Optional.of(admin));
        when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(Claim.class))).thenAnswer(i -> i.getArgument(0));

        var command = new UpdateClaimStatusCommand(claimId, ClaimStatus.UNDER_REVIEW, adminId);
        useCase.execute(command);

        // Capture events published to eventPublisher (use case clears domain events after publish)
        var eventCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.atLeastOnce()).publishEvent(eventCaptor.capture());

        ClaimStatusChanged event = eventCaptor.getAllValues().stream()
            .filter(e -> e instanceof ClaimStatusChanged)
            .map(e -> (ClaimStatusChanged) e)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No ClaimStatusChanged event was published"));

        assertEquals(claimantId, event.changedBy(),
            "BUG: event attributes change to claim owner, not admin");
    }
}