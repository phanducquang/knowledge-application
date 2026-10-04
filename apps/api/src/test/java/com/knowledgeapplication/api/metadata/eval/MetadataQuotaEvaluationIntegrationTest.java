package com.knowledgeapplication.api.metadata.eval;

import com.knowledgeapplication.api.ai.quota.*;
import java.time.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MetadataQuotaEvaluationIntegrationTest {
    MetadataQuotaDatabase db;
    @BeforeAll void start() { db=new MetadataQuotaDatabase(); }
    @AfterAll void close() { if(db!=null) db.close(); }
    @BeforeEach void clear() { db.jdbc.sql("DELETE FROM ai_quota_usage").update(); }
    @Test void isolatedProductionQuotaCountsRequestsAndTokensPersistsAcrossLimiter() {
        var quota=new AiQuotaProperties(true,10,200000,400,2.5,ZoneOffset.UTC,null);
        assertThat(db.limiter.reserve(AiQuotaLimiter.Purpose.METADATA,quota,100)).isTrue();
        assertThat(db.usage()).isEqualTo(new MetadataQuotaDatabase.Usage(1,40));
        assertThat(db.jdbc.sql("SELECT quota_key FROM ai_quota_usage ORDER BY quota_key").query(String.class).list())
                .containsExactly("metadata-generation-day","metadata-generation-minute");
        assertThat(db.jdbc.sql("SELECT tablename FROM pg_tables WHERE schemaname='public'").query(String.class).list()).containsExactly("ai_quota_usage");
    }
    @Test void minutePacingWaitIsBoundedAndMakesNoReservation() {
        var quota=new AiQuotaProperties(true,1,200000,400,2.5,ZoneOffset.UTC,null);
        assertThat(db.limiter.reserve(AiQuotaLimiter.Purpose.METADATA,quota,100)).isTrue();
        var reservedMinute=db.jdbc.sql("SELECT window_start FROM ai_quota_usage WHERE quota_key='metadata-generation-minute'")
                .query(java.time.OffsetDateTime.class).single().toInstant();
        var pacer=new MetadataQuotaDatabase.QuotaPacer(db.jdbc,quota,Clock.fixed(reservedMinute.plusSeconds(1),ZoneOffset.UTC),0,m->{throw new AssertionError("must not wait");});
        assertThatThrownBy(()->pacer.await(100)).isInstanceOf(IllegalStateException.class);
        assertThat(db.usage().requests()).isEqualTo(1);
    }
    @Test void pacingNeverWeakensDailyQuotaOrOversizedMinuteRequest() {
        var quota=new AiQuotaProperties(true,10,100,1,2.5,ZoneOffset.UTC,null);
        var pacer=new MetadataQuotaDatabase.QuotaPacer(db.jdbc,quota,Clock.systemUTC(),180,m->{throw new AssertionError("no sleep on daily denial");});
        assertThatThrownBy(()->pacer.await(1000)).isInstanceOf(IllegalStateException.class);
        assertThat(db.limiter.reserve(AiQuotaLimiter.Purpose.METADATA,quota,100)).isTrue();
        assertThatThrownBy(()->pacer.await(100)).isInstanceOf(IllegalStateException.class);
        assertThat(db.usage().requests()).isEqualTo(1);
    }
}
