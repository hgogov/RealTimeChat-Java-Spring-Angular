package com.chatapp.backend.config;

import com.chatapp.backend.filter.JwtAuthFilter;
import com.chatapp.backend.service.ChatRoomService;
import com.chatapp.backend.service.CustomUserDetailsService;
import com.chatapp.backend.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.messaging.access.intercept.AuthorizationChannelInterceptor;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;
    private final JwtUtils jwtUtils;
    private final ChatRoomService chatRoomService;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    public SecurityConfig(CustomUserDetailsService customUserDetailsService, JwtUtils jwtUtils, ChatRoomService chatRoomService) {
        this.customUserDetailsService = customUserDetailsService;
        this.jwtUtils = jwtUtils;
        this.chatRoomService = chatRoomService;
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter() {
        return new JwtAuthFilter(jwtUtils, customUserDetailsService);
    }

    private static final String[] SWAGGER_WHITELIST = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/swagger-resources/**",
            "/webjars/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(request -> {
                    CorsConfiguration config = new CorsConfiguration();

                    System.out.println("--- Spring Security CORS Filter ---");
                    System.out.println("Evaluating request for Origin: " + request.getHeader("Origin"));
                    System.out.println("Allowed Origins are: " + allowedOrigins);
                    System.out.println("---------------------------------");

                    config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
                    config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                    config.setAllowedHeaders(Arrays.asList(
                            "Origin", "Content-Type", "Accept", "Authorization",
                            "X-Requested-With", "Access-Control-Request-Method", "Access-Control-Request-Headers"
                    ));
                    config.setExposedHeaders(Arrays.asList(
                            "Origin", "Content-Type", "Accept", "Authorization",
                            "Access-Control-Allow-Origin", "Access-Control-Allow-Credentials"
                    ));
                    config.setAllowCredentials(true);
                    config.setMaxAge(3600L);

                    return config;
                }))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**", "/ws/**").permitAll()
                        .requestMatchers(SWAGGER_WHITELIST).permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .addFilterBefore(jwtAuthFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }


    @Bean
    public static MessageMatcherDelegatingAuthorizationManager.Builder messageMatcherDelegatingAuthorizationManagerBuilder() {
        return MessageMatcherDelegatingAuthorizationManager.builder();
    }

    @Bean
    AuthorizationManager<Message<?>> messageAuthorizationManager(MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        messages
                .simpTypeMatchers(SimpMessageType.CONNECT, SimpMessageType.HEARTBEAT, SimpMessageType.UNSUBSCRIBE, SimpMessageType.DISCONNECT).permitAll()
                .simpDestMatchers("/app/**").authenticated()
                .simpSubscribeDestMatchers("/topic/chat/{roomId}/**", "/topic/typing/{roomId}/**").access(
                        (authenticationSupplier, context) -> {
                            Authentication authentication = authenticationSupplier.get();
                            if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
                                return new AuthorizationDecision(false);
                            }
                            String username = authentication.getName();
                            String roomId = context.getVariables().get("roomId");
                            if (roomId == null) {
                                return new AuthorizationDecision(false);
                            }
                            boolean isMember = chatRoomService.isUserMemberOfRoom(username, roomId);
                            return new AuthorizationDecision(isMember);
                        }
                )
                .simpSubscribeDestMatchers("/topic/presence/**", "/user/**").authenticated()
                .simpTypeMatchers(SimpMessageType.MESSAGE, SimpMessageType.SUBSCRIBE).denyAll()
                .anyMessage().denyAll();

        return messages.build();
    }

    @Bean
    public AuthorizationChannelInterceptor messageAuthorizationChannelInterceptor(AuthorizationManager<Message<?>> authorizationManager) {
        return new AuthorizationChannelInterceptor(authorizationManager);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}