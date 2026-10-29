package ai.zerotrace.zerowatch;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ZeroWatchApplication {
    public static void main(String[] args) { SpringApplication.run(ZeroWatchApplication.class, args); }
    @Bean Clock clock() { return Clock.systemUTC(); }
}
