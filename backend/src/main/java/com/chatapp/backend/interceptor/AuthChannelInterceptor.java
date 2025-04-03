package com.chatapp.backend.interceptor;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuthChannelInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            System.out.println(">>> AuthChannelInterceptor: Processing CONNECT frame");
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
                accessor.setUser(auth);
                System.out.println(">>> AuthChannelInterceptor: Setting user on accessor from SecurityContext: " + auth.getName());
            } else {
                System.out.println(">>> AuthChannelInterceptor: No authenticated user found in SecurityContext during CONNECT processing.");
            }
        }
        else if (accessor != null && accessor.getUser() != null) {
            System.out.println(">>> AuthChannelInterceptor: User " + accessor.getUser().getName() + " sending " + accessor.getCommand());
        } else if (accessor != null) {
            System.out.println(">>> AuthChannelInterceptor: Processing " + accessor.getCommand() + " without user principal on accessor.");
        }

        return message;
    }
}
