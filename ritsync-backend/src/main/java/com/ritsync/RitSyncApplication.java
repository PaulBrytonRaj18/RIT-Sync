package com.ritsync;

import java.time.ZoneId;
import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class RitSyncApplication {
    public static void main(String[] args) {
        // Session times are wall-clock times entered by teachers (no offset), and compared with
        // LocalDateTime.now(). Pin the JVM zone to the institution's zone so a server running in
        // UTC (e.g. Docker) doesn't shift every class by hours. Set APP_TIMEZONE, e.g. Asia/Kolkata.
        String zone = System.getenv("APP_TIMEZONE");
        if (zone != null && !zone.isBlank()) {
            TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(zone.trim())));
        }
        SpringApplication.run(RitSyncApplication.class, args);
    }
}
