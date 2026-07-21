package com.human.ev_relay_mes.Config;

import com.human.ev_relay_mes.Security.RestAccessDeniedHandler;
import com.human.ev_relay_mes.Security.RestAuthenticationEntryPoint;
import com.human.ev_relay_mes.Security.RestSessionInformationExpiredStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.ConcurrentSessionControlAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final RestSessionInformationExpiredStrategy sessionInformationExpiredStrategy;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy(
            CsrfTokenRepository csrfTokenRepository,
            SessionRegistry sessionRegistry) {
        ConcurrentSessionControlAuthenticationStrategy concurrentSessionStrategy =
                new ConcurrentSessionControlAuthenticationStrategy(sessionRegistry);
        concurrentSessionStrategy.setMaximumSessions(1);

        return new CompositeSessionAuthenticationStrategy(List.of(
                concurrentSessionStrategy,
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokenRepository),
                new RegisterSessionAuthenticationStrategy(sessionRegistry)
        ));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CsrfTokenRepository csrfTokenRepository,
            SecurityContextRepository securityContextRepository,
            SessionRegistry sessionRegistry) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers("/api/collector/**"))
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository))
                .sessionManagement(session -> {
                    session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED);
                    session.maximumSessions(1)
                            .sessionRegistry(sessionRegistry)
                            .expiredSessionStrategy(sessionInformationExpiredStrategy);
                })
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/csrf", "/api/collector/**", "/error").permitAll()
                        .requestMatchers("/api/members/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/machines/status")
                                .hasAnyRole("ADMIN", "MANAGER", "OPERATOR")
                        .requestMatchers(HttpMethod.POST, "/api/production-logs")
                                .hasAnyRole("ADMIN", "MANAGER", "OPERATOR")
                        .requestMatchers(HttpMethod.POST,
                                "/api/quality/inspections", "/api/quality/defects", "/api/machines/alarms")
                                .hasAnyRole("ADMIN", "MANAGER", "OPERATOR")
                        .requestMatchers(HttpMethod.PATCH, "/api/quality/defects/*/confirm")
                                .hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers(HttpMethod.PATCH, "/api/machines/alarms/*/clear")
                                .hasAnyRole("ADMIN", "MANAGER", "OPERATOR")
                        .requestMatchers(HttpMethod.GET,
                                "/api/items/**", "/api/boms/**", "/api/processes/**",
                                "/api/machines/**", "/api/defect-codes/**", "/api/alarm-codes/**",
                                "/api/material-lots/**", "/api/work-orders/**", "/api/lots/**",
                                "/api/workers/**", "/api/inspection-standards/**")
                                .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/production-logs/**")
                                .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/quality/**")
                                .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/mes/**")
                                .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/mes/order")
                                .hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers(HttpMethod.POST, "/api/mes/material/inbound")
                                .hasAnyRole("ADMIN", "MANAGER", "OPERATOR")
                        .requestMatchers(
                                "/api/items/**", "/api/boms/**", "/api/processes/**",
                                "/api/machines/**", "/api/defect-codes/**", "/api/alarm-codes/**",
                                "/api/workers/**", "/api/inspection-standards/**")
                                .hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers("/api/material-lots/**")
                                .hasAnyRole("ADMIN", "MANAGER", "OPERATOR")
                        .requestMatchers("/api/work-orders/**")
                                .hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers("/api/lots/**")
                                .hasAnyRole("ADMIN", "MANAGER", "OPERATOR")
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());

        return http.build();
    }
}
