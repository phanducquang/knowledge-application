package com.knowledgeapplication.api.attachment.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AttachmentCleanupSchedulerActivationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> context.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(SchedulerTestConfiguration.class)
            .withPropertyValues(
                    "app.knowledge.attachment-cleanup.interval=PT1H",
                    "app.knowledge.attachment-cleanup.initial-delay=PT1H"
            );

    @Test
    void enabledCleanupRegistersItsScheduledMethodWithSpring() {
        contextRunner
                .withPropertyValues("app.knowledge.attachment-cleanup.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(AttachmentCleanupScheduler.class);
                    assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class);

                    var scheduling = context.getBean(ScheduledAnnotationBeanPostProcessor.class);
                    assertThat(scheduling.getScheduledTasks())
                            .singleElement()
                            .satisfies(task -> assertThat(task.toString())
                                    .contains("AttachmentCleanupScheduler.cleanup"));
                });
    }

    @Test
    void disabledCleanupDoesNotCreateOrRegisterScheduler() {
        contextRunner
                .withPropertyValues("app.knowledge.attachment-cleanup.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AttachmentCleanupScheduler.class);
                    assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class);
                    assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks())
                            .isEmpty();
                });
    }

    @Test
    void nonPositiveIntervalFailsContextStartup() {
        contextRunner
                .withPropertyValues(
                        "app.knowledge.attachment-cleanup.enabled=true",
                        "app.knowledge.attachment-cleanup.interval=PT0S"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void negativeInitialDelayFailsContextStartup() {
        contextRunner
                .withPropertyValues(
                        "app.knowledge.attachment-cleanup.enabled=true",
                        "app.knowledge.attachment-cleanup.initial-delay=-PT1S"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @Import({AttachmentCleanupSchedulingConfiguration.class, AttachmentCleanupScheduler.class})
    static class SchedulerTestConfiguration {

        @Bean
        AttachmentCleanupService attachmentCleanupService() {
            return mock(AttachmentCleanupService.class);
        }
    }
}
