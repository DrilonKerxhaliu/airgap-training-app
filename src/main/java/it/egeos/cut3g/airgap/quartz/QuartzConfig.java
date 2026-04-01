package it.egeos.cut3g.airgap.quartz;

import it.egeos.cut3g.airgap.service.util.RuntimeConfigService;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Value("${airgap.cleanup.cron}")
    private String cleanupCron;

    @Autowired
    private RuntimeConfigService runtimeConfigService;

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
                                .withIntervalInHours(runtimeConfigService.getAutoPackagingHours())
                                .repeatForever()
                )
                .build();
    }

    /**
    * SIZE-BASED PACKAGING
    **/

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
                                .withIntervalInMinutes(runtimeConfigService.getSizeCheckIntervalMin())
                                .repeatForever()
                )
                .build();
    }

    /**
     * ARCHIVE CLEANUP (RETENTION)
     **/

    @Bean
    public JobDetail archiveCleanupJobDetail() {
        return newJob(ArchiveCleanupJob.class)
                .withIdentity("archiveCleanupJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger archiveCleanupTrigger() {
        return newTrigger()
                .forJob(archiveCleanupJobDetail())
                .withIdentity("archiveCleanupTrigger")
                .withSchedule(
                        CronScheduleBuilder.cronSchedule(cleanupCron)
                )
                .build();
    }
}
