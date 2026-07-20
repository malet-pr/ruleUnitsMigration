package org.acme.ruleunits.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnapshotSlotTest {
    @Test
    void retirementRejectsNewLeasesAndClosesAfterTheLastExistingLease() {
        FakeSnapshotRuntime runtime = new FakeSnapshotRuntime(17);
        SnapshotSlot slot = new SnapshotSlot(runtime, failure -> {});

        assertThat(slot.tryAcquire()).isTrue();
        assertThat(slot.tryAcquire()).isTrue();
        assertThat(slot.leaseCount()).isEqualTo(2);

        slot.retire();

        assertThat(slot.isRetired()).isTrue();
        assertThat(slot.tryAcquire()).isFalse();
        assertThat(runtime.closeCalls()).isZero();

        slot.release();
        assertThat(runtime.closeCalls()).isZero();
        slot.release();
        assertThat(runtime.closeCalls()).isEqualTo(1);

        slot.retire();
        assertThat(runtime.closeCalls()).isEqualTo(1);
    }

    @Test
    void retirementReportsOnlySanitizedCloseFailureMetadata() {
        FakeSnapshotRuntime runtime = new FakeSnapshotRuntime("ACTIVITY_RULES", 17, true);
        List<SnapshotRetirementFailure> failures = new ArrayList<>();
        SnapshotSlot slot = new SnapshotSlot(runtime, failures::add);

        slot.retire();

        assertThat(failures).containsExactly(new SnapshotRetirementFailure(
                "ACTIVITY_RULES", 17, "IllegalStateException",
                "Failed to release compiled rule-set snapshot resources"));
        assertThat(failures.getFirst().summary()).doesNotContain("unsanitized");
    }

    @Test
    void releaseWithoutAnAcquiredLeaseIsRejected() {
        SnapshotSlot slot = new SnapshotSlot(new FakeSnapshotRuntime(17), failure -> {});

        assertThatThrownBy(slot::release)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("underflow");
    }

    @Test
    void aFailureReporterCannotMakeRetirementFail() {
        FakeSnapshotRuntime runtime = new FakeSnapshotRuntime("ACTIVITY_RULES", 17, true);
        SnapshotSlot slot = new SnapshotSlot(runtime, failure -> {
            throw new IllegalStateException("reporting failed");
        });

        assertThatCode(slot::retire).doesNotThrowAnyException();
        assertThat(runtime.closeCalls()).isEqualTo(1);
    }
}
