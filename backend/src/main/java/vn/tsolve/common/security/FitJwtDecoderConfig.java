package vn.tsolve.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import vn.tsolve.common.config.TrackerProperties;

@Configuration
public class FitJwtDecoderConfig {

    /**
     * Verifies FITs against Atlassian's JWKS. RS256 only, so {@code alg: none} and HS256 tokens fail (edge case 2).
     * Nimbus caches the key set and refetches it once when a FIT names an unknown key id (AC-001.3).
     */
    @Bean
    public JwtDecoder fitJwtDecoder(TrackerProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(
                        properties.forgeJwksUri().toString())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(FitValidators.forApp(properties.jiraAppId()));
        return decoder;
    }
}
