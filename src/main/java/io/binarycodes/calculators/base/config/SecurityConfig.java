package io.binarycodes.calculators.base.config;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Spring Security is present for the controls it brings — response headers, CSRF,
 * session policy — not to authenticate anyone. Every view is public, declared
 * per-view with {@code @AnonymousAllowed}; Vaadin denies any view that carries no
 * access annotation, so a future non-public view is protected by default rather
 * than by remembering to add something.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.with(VaadinSecurityConfigurer.vaadin(), configurer -> {
        });
        // styles.css pulls in its sibling partials by @import, and Vaadin's defaults
        // permit only styles.css itself — without this the app loads unstyled.
        http.authorizeHttpRequests(auth -> auth.requestMatchers("/*.css").permitAll());
        http.headers(headers -> {
            headers.referrerPolicy(referrer ->
                    referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
            // Spring Security normally writes its headers as the response commits,
            // which never happens for the response Vaadin renders the page into —
            // static resources got the headers and the page itself got none.
            headers.addObjectPostProcessor(new ObjectPostProcessor<HeaderWriterFilter>() {
                @Override
                public <O extends HeaderWriterFilter> O postProcess(O filter) {
                    filter.setShouldWriteHeadersEagerly(true);
                    return filter;
                }
            });
        });
        return http.build();
    }
}
