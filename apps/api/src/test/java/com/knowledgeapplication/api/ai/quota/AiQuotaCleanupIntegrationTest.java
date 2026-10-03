package com.knowledgeapplication.api.ai.quota;

import com.knowledgeapplication.api.testsupport.TestContainerImages;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(properties = "app.ai.quota-cleanup.enabled=false")
class AiQuotaCleanupIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(TestContainerImages.POSTGRES);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("app.object-storage.endpoint", () -> "http://127.0.0.1:19000");
        r.add("app.object-storage.bucket", () -> "unused-quota-tests");
        r.add("app.object-storage.access-key", () -> "test-key");
        r.add("app.object-storage.secret-key", () -> "test-secret");
        r.add("app.knowledge.attachment-cleanup.enabled", () -> "false");
    }
    @Autowired JdbcClient jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired AiQuotaCleanupService disabledService;
    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");
    private Clock clock;

    @BeforeEach void reset() {
        jdbc.sql("TRUNCATE ai_quota_usage").update();
        clock = Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")); // Cutoffs must not use JVM/Clock local dates.
    }
    AiQuotaCleanupService cleanup(int batch) { return cleanup(batch, Duration.ofDays(2), Duration.ofDays(30)); }
    AiQuotaCleanupService cleanup(int batch, Duration minute, Duration daily) {
        return new AiQuotaCleanupService(jdbc, manager, new AiQuotaCleanupProperties(true, minute, daily,
                Duration.ofHours(6), Duration.ofMinutes(10), batch), clock);
    }
    void row(String key, Instant end) { row(key, end.minusSeconds(60), end); }
    void row(String key, Instant start, Instant end) {
        jdbc.sql("INSERT INTO ai_quota_usage(quota_key,window_start,window_end,request_count,estimated_input_tokens) VALUES(:key,:start,:end,7,33)")
                .param("key", key).param("start", start.atOffset(ZoneOffset.UTC)).param("end", end.atOffset(ZoneOffset.UTC)).update();
    }
    List<String> keys() { return jdbc.sql("SELECT quota_key FROM ai_quota_usage ORDER BY quota_key,window_start").query(String.class).list(); }
    AiQuotaProperties quota(long rpm, long tpm, long rpd, AiQuotaProperties.Budget background) {
        return new AiQuotaProperties(true, rpm, tpm, rpd, 2.5, ZoneId.of("America/Los_Angeles"), background);
    }

    @Test void eligibilityUsesSeparateStrictEndCutoffsAndRetainsUnknownActiveAndFutureRows() {
        row("old-minute", NOW.minus(Duration.ofDays(10)));
        row("recent-minute", NOW.minus(Duration.ofDays(1)));
        row("boundary-minute", NOW.minus(Duration.ofDays(2)));
        row("old-day", NOW.minus(Duration.ofDays(31)));
        row("recent-day", NOW.minus(Duration.ofDays(10)));
        row("boundary-day", NOW.minus(Duration.ofDays(30)));
        row("active-minute", NOW.plusSeconds(30));
        row("active-day", NOW.plusSeconds(3600));
        row("future-day", NOW.plus(Duration.ofDays(2)), NOW.plus(Duration.ofDays(3)));
        row("unknown-window", NOW.minus(Duration.ofDays(100)));
        assertThat(cleanup(100).cleanupOnce()).isEqualTo(new AiQuotaCleanupService.Summary(true, 1, 1));
        assertThat(keys()).containsExactly("active-day", "active-minute", "boundary-day", "boundary-minute",
                "future-day", "recent-day", "recent-minute", "unknown-window");
        assertThat(cleanup(100).cleanupOnce()).isEqualTo(new AiQuotaCleanupService.Summary(true, 0, 0));
    }

    @Test void tinyRetentionStillRetainsExactNowActiveFutureAndAllCurrentRpdCounters() {
        for (String key : List.of("embedding-global-day", "embedding-background-day", "ask-generation-day")) row(key, NOW.plusSeconds(3600));
        row("embedding-global-minute", NOW.plusSeconds(30));
        row("ends-now-minute", NOW);
        row("future-minute", NOW.plusSeconds(60), NOW.plusSeconds(120));
        row("expired-minute", NOW.minusSeconds(1));
        var before = jdbc.sql("SELECT quota_key,request_count,estimated_input_tokens FROM ai_quota_usage WHERE quota_key != 'expired-minute' ORDER BY quota_key").query().listOfRows();
        assertThat(cleanup(100, Duration.ofMillis(1), Duration.ofMillis(1)).cleanupOnce().minuteRowsDeleted()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT quota_key,request_count,estimated_input_tokens FROM ai_quota_usage ORDER BY quota_key").query().listOfRows()).isEqualTo(before);
    }

    @Test void batchIsTotalBoundAndOrdersEndThenKeyThenStart() {
        Instant end = NOW.minus(Duration.ofDays(40));
        row("a-minute", end.minusSeconds(1));
        row("b-day", end.minusSeconds(120), end);
        row("b-day", end.minusSeconds(60), end);
        row("c-minute", end);
        row("d-day", end.plusSeconds(1));
        assertThat(cleanup(2).cleanupOnce()).isEqualTo(new AiQuotaCleanupService.Summary(true, 1, 1));
        assertThat(keys()).containsExactly("b-day", "c-minute", "d-day");
        assertThat(jdbc.sql("SELECT window_start FROM ai_quota_usage WHERE quota_key='b-day'").query(OffsetDateTime.class).single().toInstant()).isEqualTo(end.minusSeconds(60));
        assertThat(cleanup(2).cleanupOnce()).isEqualTo(new AiQuotaCleanupService.Summary(true, 1, 1));
        assertThat(keys()).containsExactly("d-day");
        assertThat(cleanup(2).cleanupOnce().dailyRowsDeleted()).isEqualTo(1);
    }

    @Test void dstDaysUsePersistedEndsRatherThan24HourDuration() {
        for (LocalDate date : List.of(LocalDate.of(2026, 3, 8), LocalDate.of(2025, 11, 2))) {
            var start = date.atStartOfDay(ZoneId.of("America/Los_Angeles")).toInstant();
            var end = date.plusDays(1).atStartOfDay(ZoneId.of("America/Los_Angeles")).toInstant();
            assertThat(Duration.between(start, end).toHours()).isIn(23L, 25L);
            row("ask-generation-day", start, end);
            clock = Clock.fixed(end.plus(Duration.ofDays(29)), ZoneOffset.UTC);
            assertThat(cleanup(100).cleanupOnce().dailyRowsDeleted()).isZero();
            clock = Clock.fixed(end.plus(Duration.ofDays(30)), ZoneOffset.UTC);
            assertThat(cleanup(100).cleanupOnce().dailyRowsDeleted()).isZero();
            clock = Clock.fixed(end.plus(Duration.ofDays(30)).plusSeconds(1), ZoneOffset.UTC);
            assertThat(cleanup(100).cleanupOnce().dailyRowsDeleted()).isEqualTo(1);
        }
    }

    @Test void disabledCleanupDoesNothingAndLimiterStillReserves() {
        row("old-minute", NOW.minus(Duration.ofDays(10)));
        assertThat(disabledService.cleanupOnce()).isEqualTo(new AiQuotaCleanupService.Summary(false, 0, 0));
        assertThat(keys()).containsExactly("old-minute");
        assertThat(new AiQuotaLimiter(jdbc, manager, clock).reserve(AiQuotaLimiter.Purpose.ASK, quota(1, 100, 1, null), 10)).isTrue();
    }

    @Test void cleanupPreservesRpmTpmRpdGlobalBackgroundAndReconstructedLimiter() {
        row("old-minute", NOW.minus(Duration.ofDays(10)));
        var q = quota(4, 100, 5, new AiQuotaProperties.Budget(2, 80, 3));
        var limiter = new AiQuotaLimiter(jdbc, manager, clock);
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING_BACKGROUND, q, 10)).isTrue();
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING_BACKGROUND, q, 10)).isTrue();
        assertThat(cleanup(100).cleanupOnce().minuteRowsDeleted()).isEqualTo(1);
        limiter = new AiQuotaLimiter(jdbc, manager, clock);
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING_BACKGROUND, q, 10)).isFalse(); // background RPM
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING, q, 10)).isTrue();
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING, q, 10)).isTrue();
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING, q, 10)).isFalse(); // global RPM
        var ask = quota(10, 100, 1, null);
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.ASK, ask, 251)).isFalse(); // estimated TPM
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.ASK, ask, 10)).isTrue();
        clock = Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC);
        cleanup(100).cleanupOnce();
        limiter = new AiQuotaLimiter(jdbc, manager, clock);
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.ASK, ask, 10)).isFalse(); // persisted generation RPD
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING_BACKGROUND, q, 10)).isTrue();
        clock = Clock.fixed(NOW.plusSeconds(120), ZoneOffset.UTC);
        cleanup(100).cleanupOnce();
        limiter = new AiQuotaLimiter(jdbc, manager, clock);
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING_BACKGROUND, q, 10)).isFalse(); // background RPD
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING, q, 10)).isFalse(); // global RPD
        assertThat(jdbc.sql("SELECT request_count FROM ai_quota_usage WHERE quota_key='embedding-global-day' AND window_end > :now")
                .param("now", NOW.atOffset(ZoneOffset.UTC)).query(Long.class).single()).isEqualTo(5);
    }

    @Test void secondCleanerSkipsWithoutWaitingAndReservationDoesNotShareCleanupLock() throws Exception {
        row("old-minute", NOW.minus(Duration.ofDays(10)));
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            // Pause the actual first cleanup immediately after its advisory lock, not a simulated cleaner.
            Clock firstClock = new Clock() {
                @Override public ZoneId getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(ZoneId zone) { return this; }
                @Override public Instant instant() {
                    locked.countDown();
                    try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("lock release timeout"); }
                    catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); }
                    return NOW;
                }
            };
            var firstService = new AiQuotaCleanupService(jdbc, manager, new AiQuotaCleanupProperties(true,
                    Duration.ofDays(2), Duration.ofDays(30), Duration.ofHours(6), Duration.ZERO, 100), firstClock);
            var first = pool.submit(firstService::cleanupOnce);
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(pool.submit(() -> cleanup(100).cleanupOnce()).get(3, TimeUnit.SECONDS)).isEqualTo(new AiQuotaCleanupService.Summary(false, 0, 0));
            assertThat(pool.submit(() -> new AiQuotaLimiter(jdbc, manager, clock).reserve(AiQuotaLimiter.Purpose.ASK, quota(10, 100, 2, null), 10)).get(3, TimeUnit.SECONDS)).isTrue();
            assertThat(keys()).contains("old-minute");
            release.countDown(); assertThat(first.get(3, TimeUnit.SECONDS).minuteRowsDeleted()).isEqualTo(1);
            assertThat(cleanup(100).cleanupOnce()).isEqualTo(new AiQuotaCleanupService.Summary(true, 0, 0));
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test void parallelCleanupAndReplicaReservationsKeepActiveCountersAtomic() throws Exception {
        for (int i = 0; i < 20; i++) row("old-minute", NOW.minus(Duration.ofDays(10)).minusSeconds(i));
        var q = quota(100, 10000, 5, null); var pool = Executors.newFixedThreadPool(8);
        try {
            var tasks = new ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 30; i++) tasks.add(i % 3 == 0 ? () -> { cleanup(3).cleanupOnce(); return false; }
                    : () -> new AiQuotaLimiter(jdbc, manager, clock).reserve(AiQuotaLimiter.Purpose.ASK, q, 10));
            int accepted = 0; for (var result : pool.invokeAll(tasks)) if (result.get(10, TimeUnit.SECONDS)) accepted++;
            assertThat(accepted).isEqualTo(5);
            assertThat(jdbc.sql("SELECT request_count FROM ai_quota_usage WHERE quota_key='ask-generation-day'").query(Long.class).single()).isEqualTo(5);
            assertThat(jdbc.sql("SELECT request_count FROM ai_quota_usage WHERE quota_key='ask-generation-minute'").query(Long.class).single()).isEqualTo(5);
            assertThat(jdbc.sql("SELECT estimated_input_tokens FROM ai_quota_usage WHERE quota_key='ask-generation-day'").query(Long.class).single()).isEqualTo(20);
            assertThat(jdbc.sql("SELECT estimated_input_tokens FROM ai_quota_usage WHERE quota_key='ask-generation-minute'").query(Long.class).single()).isEqualTo(20);
            assertThat(new AiQuotaLimiter(jdbc, manager, clock).reserve(AiQuotaLimiter.Purpose.ASK, q, 10)).isFalse();
            assertThat(jdbc.sql("SELECT count(*) FROM ai_quota_usage WHERE quota_key='old-minute'").query(Long.class).single()).isLessThan(20);
        } finally { pool.shutdownNow(); }
    }

    @Test void lockedHistoryIsRetainedWithoutBlockingAndPickedUpOnALaterCycle() throws Exception {
        row("locked-minute", NOW.minus(Duration.ofDays(11)));
        row("available-minute", NOW.minus(Duration.ofDays(10)));
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var holder = pool.submit(() -> new TransactionTemplate(manager).execute(status -> {
                jdbc.sql("SELECT quota_key FROM ai_quota_usage WHERE quota_key='locked-minute' FOR UPDATE").query(String.class).single();
                locked.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("row lock release timeout"); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); }
                return true;
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(pool.submit(() -> cleanup(1).cleanupOnce()).get(3, TimeUnit.SECONDS).minuteRowsDeleted()).isEqualTo(1);
            assertThat(keys()).containsExactly("locked-minute");
            release.countDown(); assertThat(holder.get(3, TimeUnit.SECONDS)).isTrue();
            assertThat(cleanup(1).cleanupOnce().minuteRowsDeleted()).isEqualTo(1);
        } finally { release.countDown(); pool.shutdownNow(); }
    }
}
