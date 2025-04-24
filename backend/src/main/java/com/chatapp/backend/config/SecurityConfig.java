package com.chatapp.backend.config;

import com.chatapp.backend.filter.JwtAuthFilter;
import com.chatapp.backend.service.ChatRoomService;
import com.chatapp.backend.service.CustomUserDetailsService;
import com.chatapp.backend.utils.JwtUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;
    private final JwtUtils jwtUtils;
    private final ChatRoomService chatRoomService;

    public SecurityConfig(CustomUserDetailsService customUserDetailsService, JwtUtils jwtUtils, ChatRoomService chatRoomService) {
        this.customUserDetailsService = customUserDetailsService;
        this.jwtUtils = jwtUtils;
        this.chatRoomService = chatRoomService;
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter() {
        return new JwtAuthFilter(jwtUtils, customUserDetailsService);
    }

    // Common Swagger paths
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
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
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
        System.out.println("--- Creating MessageMatcherDelegatingAuthorizationManager.Builder Bean ---");
        return MessageMatcherDelegatingAuthorizationManager.builder();
    }

    @Bean
    AuthorizationManager<Message<?>> messageAuthorizationManager(MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        System.out.println("--- Configuring WebSocket Message Authorization ---");
        messages
                .simpTypeMatchers(SimpMessageType.CONNECT, SimpMessageType.HEARTBEAT, SimpMessageType.UNSUBSCRIBE, SimpMessageType.DISCONNECT).permitAll()
                // Secure actual messages sent TO the application
                .simpDestMatchers("/app/**").authenticated()
                // --- SECURE SUBSCRIPTIONS to room-specific topics ---
                .simpSubscribeDestMatchers("/topic/chat/{roomId}/**", "/topic/typing/{roomId}/**").access(
                        (authenticationSupplier, context) -> {

                            Authentication authentication = authenticationSupplier.get();

                            if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
                                System.out.println("[Authz] Denying SUBSCRIBE - User not authenticated.");
                                return new AuthorizationDecision(false);
                            }
                            String username = authentication.getName();
                            String roomId = context.getVariables().get("roomId");
                            if (roomId == null) {
                                Object payload = context.getMessage().getPayload();
                                if (roomId == null) {
                                    System.out.println("[Authz] Denying SUBSCRIBE - Room ID missing in destination path or payload.");
                                    return new AuthorizationDecision(false);
                                }
                            }
                            boolean isMember = chatRoomService.isUserMemberOfRoom(username, roomId);
                            System.out.println("[Authz] Checking SUBSCRIBE for user '" + username + "' to room '" + roomId + "': " + (isMember ? "GRANTED" : "DENIED"));

                            return new AuthorizationDecision(isMember);
                        }
                )
                // --- END Room Subscription Security ---

                .simpSubscribeDestMatchers("/topic/presence", "/topic/presence.list").authenticated()
                .simpSubscribeDestMatchers("/user/**").authenticated()
                .simpTypeMatchers(SimpMessageType.MESSAGE, SimpMessageType.SUBSCRIBE).denyAll()
                .anyMessage().denyAll();

        System.out.println("--- WebSocket Message Authorization Configured ---");
        return messages.build();
    }

    // This bean applies the rules defined above to the message channels
    @Bean
    public AuthorizationChannelInterceptor messageAuthorizationChannelInterceptor(AuthorizationManager<Message<?>> authorizationManager) {
        AuthorizationChannelInterceptor interceptor = new AuthorizationChannelInterceptor(authorizationManager);
        return interceptor;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config
    ) throws Exception {
        return config.getAuthenticationManager();
    }
}