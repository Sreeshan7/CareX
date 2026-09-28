package com.carex.leave.common.time;

import com.carex.leave.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {
    /** Tests register a {@code @Primary} mutable clock to drive escalation deterministically. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public BusinessCalendar businessCalendar(Clock clock, AppProperties props) {
        return new BusinessCalendar(clock, ZoneId.of(props.zone()));
    }
}
