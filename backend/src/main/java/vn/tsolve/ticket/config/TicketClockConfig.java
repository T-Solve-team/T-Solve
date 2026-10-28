package vn.tsolve.ticket.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TicketClockConfig {

    /** Dates the user types (resolved_date) are judged in Vietnam time (spec AC-002.7). */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Ho_Chi_Minh"));
    }
}
