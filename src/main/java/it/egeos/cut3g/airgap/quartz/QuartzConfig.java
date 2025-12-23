package it.egeos.cut3g.airgap.quartz;

import org.quartz.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;
import static org.quartz.JobBuilder.newJob;

/**
 * Quartz scheduling:
 * - AutoPackagingJob: every airgap.auto.hours hours
 */
@Configuration
public class QuartzConfig {

    @Value("${airgap.auto.hours}")
    private int autoHours;

    @Bean
    public JobDetail autoPackagingJobDetail() {
        return newJob(AutoPackagingJob.class).withIdentity("autoPackagingJob").storeDurably().build();
    }

    @Bean
    public Trigger autoPackagingTrigger() {
        return newTrigger()
                .forJob(autoPackagingJobDetail())
                .withIdentity("autoPackagingTrigger")
                .withSchedule(simpleSchedule().withIntervalInHours(autoHours).repeatForever())
                .build();
    }
}
