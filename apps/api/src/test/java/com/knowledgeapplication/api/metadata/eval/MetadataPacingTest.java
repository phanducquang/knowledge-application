package com.knowledgeapplication.api.metadata.eval;

import com.knowledgeapplication.api.metadata.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MetadataPacingTest {
    AtomicLong ticker=new AtomicLong(); List<Long> sleeps=new ArrayList<>();
    MetadataPacing pacer(int eval,int quota,int safety,int wait) {
        return new MetadataPacing(eval,quota,safety,ticker::get,new MetadataPacing.WaitBudget(ticker::get,m->{sleeps.add(m);ticker.addAndGet(m*1000000);},wait),c->{});
    }
    @Test void minuteBoundaryCannotResetSpacing() {
        ticker.set(55_000_000_000L); var pacer=pacer(10,10,250,180); pacer.await(1);
        ticker.set(60_000_000_000L); // wall clock's minute changed, monotonic elapsed is only five seconds.
        pacer.await(1); assertThat(ticker.get()).isEqualTo(61_250_000_000L); assertThat(sleeps).containsExactly(1250L);
    }
    @Test void effectiveRpmIsMinimumOfEvaluationAndMetadataQuota() {
        assertThat(pacer(12,10,250,180).intervalMillis()).isEqualTo(6250);
        assertThat(pacer(12,20,250,180).intervalMillis()).isEqualTo(5250);
    }
    @Test void ceilDivisionAndConfiguredSafetyApplied() {
        assertThat(pacer(7,20,321,180).intervalMillis()).isEqualTo(8893);
    }
    @Test void noRollingSixtySecondWindowCanExceedConfiguredCapacity() {
        for(int rpm:List.of(3,10,12,25)) {
            ticker.set(0); var pacer=pacer(rpm,30,250,600); var times=new ArrayList<Long>();
            for(int n=0;n<rpm*2;n++) { pacer.await(1); times.add(ticker.get()); }
            for(long end:times) assertThat(times.stream().filter(t->t<=end && t>=end-60_000_000_000L).count()).isLessThanOrEqualTo(rpm);
        }
    }
    @Test void realTransportStartIncludesSlowQuotaReservationBeforeSpacingNextRequest() {
        var pacer=pacer(10,10,250,180); pacer.await(1);
        ticker.addAndGet(2_000_000_000L); pacer.attemptStarted(); // reservation took two seconds, no sleep after reservation.
        pacer.await(1); assertThat(ticker.get()).isEqualTo(8_250_000_000L);
    }
    @Test void longProviderLatencyDoesNotAddUnnecessarySleep() {
        var pacer=pacer(10,10,250,180); pacer.await(1); ticker.addAndGet(10_000_000_000L); pacer.await(1); assertThat(sleeps).isEmpty();
    }
    @Test void sharedWaitBudgetIncludesDbWaitAndSpacing() {
        var wait=new MetadataPacing.WaitBudget(ticker::get,m->ticker.addAndGet(m*1000000),7);
        var pacer=new MetadataPacing(10,10,250,ticker::get,wait,c->{}); pacer.await(1);
        wait.sleep(2_000_000_000L); pacer.await(1);
        assertThatThrownBy(()->pacer.await(1)).isInstanceOf(MetadataUnavailableException.class)
                .extracting(e->((MetadataUnavailableException)e).category()).isEqualTo(MetadataFailureCategory.PACING_BUDGET);
    }
    @Test void exhaustionStopsBeforeAnotherProviderAttempt() {
        var pacer=pacer(10,10,250,0); pacer.await(1);
        assertThatThrownBy(()->pacer.await(1)).isInstanceOf(MetadataUnavailableException.class); assertThat(sleeps).isEmpty();
    }
    @Test void interruptBeforeWaitOrDuringWaitStopsAndPreservesInterruptFlag() {
        try {
            Thread.currentThread().interrupt(); assertThatThrownBy(()->pacer(10,10,250,180).await(1)).isInstanceOf(MetadataUnavailableException.class);
        } finally { Thread.interrupted(); }
        var wait=new MetadataPacing.WaitBudget(ticker::get,m->{throw new InterruptedException("secret transport detail");},180);
        var pacer=new MetadataPacing(10,10,250,ticker::get,wait,c->{}); pacer.await(1);
        try { assertThatThrownBy(()->pacer.await(1)).isInstanceOf(MetadataUnavailableException.class).hasNoCause(); assertThat(Thread.currentThread().isInterrupted()).isTrue(); }
        finally { Thread.interrupted(); }
    }
    @Test void schedulerOversleepCannotSilentlyExceedWaitBudget() {
        var wait=new MetadataPacing.WaitBudget(ticker::get,m->ticker.addAndGet(20_000_000_000L),7);
        assertThatThrownBy(()->wait.sleep(6_000_000_000L)).isInstanceOf(MetadataUnavailableException.class);
    }
    @Test void invalidPacingConfigurationRejected() {
        assertThatThrownBy(()->pacer(0,10,250,180)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->pacer(10,10,0,180)).isInstanceOf(IllegalArgumentException.class);
    }
}
