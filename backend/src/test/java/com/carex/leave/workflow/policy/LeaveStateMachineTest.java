package com.carex.leave.workflow.policy;

import com.carex.leave.common.error.ApiException;
import com.carex.leave.leave.request.LeaveStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static com.carex.leave.leave.request.LeaveStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** All 7 × 7 status × event pairs (implementation.md §21 ★). */
class LeaveStateMachineTest {

    private static final Map<String, LeaveStatus> VALID = Map.ofEntries(
            Map.entry("PENDING_MANAGER/MANAGER_APPROVE", PENDING_HR),
            Map.entry("MANAGER_ESCALATED/MANAGER_APPROVE", PENDING_HR),
            Map.entry("PENDING_MANAGER/MANAGER_REJECT", REJECTED),
            Map.entry("MANAGER_ESCALATED/MANAGER_REJECT", REJECTED),
            Map.entry("PENDING_HR/HR_APPROVE", APPROVED),
            Map.entry("HR_ESCALATED/HR_APPROVE", APPROVED),
            Map.entry("PENDING_HR/HR_REJECT", REJECTED),
            Map.entry("HR_ESCALATED/HR_REJECT", REJECTED),
            Map.entry("PENDING_MANAGER/ESCALATE", MANAGER_ESCALATED),
            Map.entry("PENDING_HR/ESCALATE", HR_ESCALATED),
            Map.entry("PENDING_MANAGER/CANCEL", CANCELLED),
            Map.entry("MANAGER_ESCALATED/CANCEL", CANCELLED),
            Map.entry("PENDING_HR/CANCEL", CANCELLED),
            Map.entry("HR_ESCALATED/CANCEL", CANCELLED),
            Map.entry("APPROVED/CANCEL", CANCELLED));

    static Stream<Arguments> allPairs() {
        return Stream.of(LeaveStatus.values()).flatMap(s -> Stream.of(LeaveEvent.values()).map(e -> Arguments.of(s, e)));
    }

    @ParameterizedTest(name = "{0} + {1}")
    @MethodSource("allPairs")
    void everyPairIsEitherTheDocumentedTransitionOrRejected(LeaveStatus s, LeaveEvent e) {
        LeaveStatus expected = VALID.get(s + "/" + e);
        if (expected != null) {
            assertThat(LeaveStateMachine.next(s, e)).isEqualTo(expected);
        } else {
            assertThat(LeaveStateMachine.allows(s, e)).isFalse();
            assertThatThrownBy(() -> LeaveStateMachine.next(s, e))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("code", "INVALID_TRANSITION");
        }
    }

    @Test
    void pendingManagerPlusHrApproveMustFail() {
        assertThat(LeaveStateMachine.allows(PENDING_MANAGER, LeaveEvent.HR_APPROVE)).isFalse();
        assertThat(LeaveStateMachine.allows(MANAGER_ESCALATED, LeaveEvent.HR_APPROVE)).isFalse();
    }

    @Test
    void pendingHrPlusManagerApproveMustFail() {
        assertThat(LeaveStateMachine.allows(PENDING_HR, LeaveEvent.MANAGER_APPROVE)).isFalse();
        assertThat(LeaveStateMachine.allows(HR_ESCALATED, LeaveEvent.MANAGER_APPROVE)).isFalse();
    }

    @Test
    void noEventMovesAManagerStageStateToApproved() {
        for (LeaveEvent e : LeaveEvent.values()) {
            assertThat(LeaveStateMachine.tryNext(PENDING_MANAGER, e)).isNotEqualTo(java.util.Optional.of(APPROVED));
            assertThat(LeaveStateMachine.tryNext(MANAGER_ESCALATED, e)).isNotEqualTo(java.util.Optional.of(APPROVED));
        }
    }

    @Test
    void terminalStatesAreTerminal() {
        for (LeaveEvent e : LeaveEvent.values()) {
            assertThat(LeaveStateMachine.allows(REJECTED, e)).isFalse();
            assertThat(LeaveStateMachine.allows(CANCELLED, e)).isFalse();
        }
    }

    @Test
    void tableHasExactlyFifteenTransitions() {
        int n = LeaveStateMachine.table().values().stream().mapToInt(Map::size).sum();
        assertThat(n).isEqualTo(VALID.size()).isEqualTo(15);
    }
}
