package ai.zerotrace.zeroremediate;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ZeroRemediateApplication {
    public static void main(String[] args) { SpringApplication.run(ZeroRemediateApplication.class, args); }
    @Bean Clock clock() { return Clock.systemUTC(); }
}
