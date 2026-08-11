package io.binarycodes.calculators.base.config;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

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
        return http.build();
    }
}
