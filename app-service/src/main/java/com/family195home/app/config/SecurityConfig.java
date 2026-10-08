package com.family195home.app.config;

import com.family195home.app.security.InternalTokenFilter;
import com.family195home.app.security.JwtAuthFilter;
import com.family195home.app.security.ProblemDetailAuthHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final InternalTokenFilter internalTokenFilter;
    private final ProblemDetailAuthHandlers authHandlers;

    public SecurityConfig(
            JwtAuthFilter jwtAuthFilter, InternalTokenFilter internalTokenFilter, ProblemDetailAuthHandlers authHandlers) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.internalTokenFilter = internalTokenFilter;
        this.authHandlers = authHandlers;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authHandlers)
                        .accessDeniedHandler(authHandlers))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/users/register", "/api/users/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        // 前端靜態檔案（打包進 src/main/resources/static），同源無需驗證
                        .anyRequest().permitAll())
                .addFilterBefore(internalTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
