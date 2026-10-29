package ai.zerotrace.zeroremediate.config;

import ai.zerotrace.zeroremediate.service.TrustedVerificationSuite;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VerificationConfig {
    @Bean public TrustedVerificationSuite trustedVerificationSuite(){return new TrustedVerificationSuite.FixtureSqlInjectionSuite();}
}
