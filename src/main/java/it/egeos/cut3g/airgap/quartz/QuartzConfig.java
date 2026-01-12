package it.egeos.cut3g.airgap.quartz;

import org.quartz.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

@Configuration
public class QuartzConfig {

    @Value("${airgap.auto.hours}")
    private int autoHours;

    @Value("${airgap.auto.size-check-interval-min}")
    private int sizeIntervalMin;

    /**
    * AUTO (TIME-BASED) PACKAGING
    **/

    @Bean
    public JobDetail autoPackagingJobDetail() {
        return newJob(AutoPackagingJob.class)
                .withIdentity("autoPackagingJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger autoPackagingTrigger() {
        return newTrigger()
                .forJob(autoPackagingJobDetail())
                .withIdentity("autoPackagingTrigger")
                .withSchedule(
                        simpleSchedule()
                                .withIntervalInHours(autoHours)
                                .repeatForever()
                )
                .build();
    }

    /**
    * SIZE-BASED PACKAGING
    **/

    /*

    @Bean
    public JobDetail sizeBasedPackagingJobDetail() {
        return newJob(SizeBasedPackagingJob.class)
                .withIdentity("sizeBasedPackagingJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger sizeBasedPackagingTrigger() {
        return newTrigger()
                .forJob(sizeBasedPackagingJobDetail())
                .withIdentity("sizeBasedPackagingTrigger")
                .withSchedule(
                        simpleSchedule()
                                .withIntervalInMinutes(sizeIntervalMin)
                                .repeatForever()
                )
                .build();
    }

    */
}
