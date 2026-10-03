package com.knowledgeapplication.api.ai.quota;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiQuotaCleanupConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AiQuotaCleanupConfiguration.class, AiQuotaCleanupScheduler.class, Dependencies.class);

    @Configuration(proxyBeanMethods = false)
    static class Dependencies {
        @Bean static ScheduledAnnotationBeanPostProcessor scheduling() { return new ScheduledAnnotationBeanPostProcessor(); }
        @Bean AiQuotaCleanupService service() { return mock(AiQuotaCleanupService.class); }
        @Bean AiQuotaLimiter limiter() { return mock(AiQuotaLimiter.class); }
    }

    @Test void defaultsRegisterOneDelayedTaskWithoutStartupCleanup() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(AiQuotaCleanupScheduler.class);
            var properties = context.getBean(AiQuotaCleanupProperties.class);
            assertThat(properties).isEqualTo(new AiQuotaCleanupProperties(true, Duration.ofDays(2),
                    Duration.ofDays(30), Duration.ofHours(6), Duration.ofMinutes(10), 1000));
            assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks())
                    .singleElement().satisfies(task -> assertThat(task.toString()).contains("AiQuotaCleanupScheduler.cleanup"));
            verifyNoInteractions(context.getBean(AiQuotaCleanupService.class), context.getBean(AiQuotaLimiter.class));
        });
    }

    @Test void customValuesBindAndDisabledModeSkipsEnabledOnlyValidation() {
        runner.withPropertyValues("app.ai.quota-cleanup.minute-retention=P3D", "app.ai.quota-cleanup.daily-retention=P40D",
                "app.ai.quota-cleanup.interval=PT12H", "app.ai.quota-cleanup.initial-delay=PT20M",
                "app.ai.quota-cleanup.batch-size=50").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(AiQuotaCleanupProperties.class)).isEqualTo(new AiQuotaCleanupProperties(true,
                    Duration.ofDays(3), Duration.ofDays(40), Duration.ofHours(12), Duration.ofMinutes(20), 50));
        });
        runner.withPropertyValues("app.ai.quota-cleanup.enabled=false", "app.ai.quota-cleanup.minute-retention=PT0S",
                "app.ai.quota-cleanup.daily-retention=-PT1S", "app.ai.quota-cleanup.interval=PT0S",
                "app.ai.quota-cleanup.initial-delay=-PT1S", "app.ai.quota-cleanup.batch-size=0").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(AiQuotaCleanupScheduler.class).hasSingleBean(AiQuotaLimiter.class);
            assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).isEmpty();
            verifyNoInteractions(context.getBean(AiQuotaCleanupService.class), context.getBean(AiQuotaLimiter.class));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"minute-retention=PT0S", "minute-retention=-P1D", "minute-retention=PT0.000001S",
            "daily-retention=PT0S", "daily-retention=P36501D", "interval=PT0S", "interval=PT1S", "interval=P366D",
            "initial-delay=-PT1S", "initial-delay=P366D", "batch-size=0", "batch-size=10001"})
    void enabledConfigurationFailsFast(String invalid) {
        runner.withPropertyValues("app.ai.quota-cleanup." + invalid).run(context -> assertThat(context).hasFailed());
    }

    @Test void schedulerMakesOneAttemptAndSuppressesUnsanitizedDatabaseErrors() {
        var service = mock(AiQuotaCleanupService.class);
        var properties = new AiQuotaCleanupProperties(true, Duration.ofDays(2), Duration.ofDays(30),
                Duration.ofHours(6), Duration.ZERO, 1000);
        var scheduler = new AiQuotaCleanupScheduler(service, properties);
        when(service.cleanupOnce()).thenThrow(new IllegalStateException("must not be logged"));
        assertThatCode(scheduler::cleanup).doesNotThrowAnyException();
        verify(service, times(1)).cleanupOnce();
    }
}
