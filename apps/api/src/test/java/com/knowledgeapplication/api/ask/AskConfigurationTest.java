package com.knowledgeapplication.api.ask;
import com.knowledgeapplication.api.ai.gemini.GeminiProperties;
import com.knowledgeapplication.api.ai.quota.AiQuotaProperties;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class AskConfigurationTest {
    @Test void disabledConfigurationNeedsNoGenerationSettingsOrKey() {
        assertThatCode(()->new AskProperties(false,null,null,null,null,0,0,0,0,0)).doesNotThrowAnyException();
        assertThat(new GeminiProperties("private-test-key").toString()).doesNotContain("private-test-key");
    }
    @Test void enabledConfigurationRejectsMissingModelInvalidUrlTimeoutOrContextBounds() {
        for(String url:new String[]{"", "file:///tmp/private","http://user:password@host","http://host?q=secret"})
            assertThatThrownBy(()->new AskProperties(true,url,"model",Duration.ofSeconds(1),Duration.ofSeconds(1),1200,24000,8,2,6)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new AskProperties(true,"http://localhost"," ",Duration.ofSeconds(1),Duration.ZERO,0,0,0,0,0)).isInstanceOf(IllegalArgumentException.class);
        for(int field=0;field<5;field++) {
            int f=field;
            assertThatThrownBy(()->new AskProperties(true,"http://localhost","model",Duration.ofSeconds(1),Duration.ofSeconds(1),f==0?0:1200,f==1?0:24000,f==2?0:8,f==3?0:2,f==4?0:6)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(()->new GeminiProperties("").requireKey()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new GeminiProperties("invalid\nkey").requireKey()).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void quotaLimitsEstimatorAndBackgroundConfigurationValidated() {
        for(long invalid:new long[]{0,-1}) assertThatThrownBy(()->new AiQuotaProperties(true,invalid,1,1,2.5,ZoneOffset.UTC,null)).isInstanceOf(IllegalArgumentException.class);
        for(double invalid:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY}) assertThatThrownBy(()->new AiQuotaProperties(true,1,1,1,invalid,ZoneOffset.UTC,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new AiQuotaProperties(true,1,1,1,2.5,ZoneOffset.UTC,new AiQuotaProperties.Budget(2,1,1))).isInstanceOf(IllegalArgumentException.class);
        assertThat(new AiQuotaProperties(true,80,24000,800,2.5,ZoneOffset.UTC,null).estimate(101)).isEqualTo(41);
    }
}
