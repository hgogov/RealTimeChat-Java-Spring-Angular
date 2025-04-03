package com.chatapp.backend.filter;

import com.chatapp.backend.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthFilter extends OncePerRequestFilter {

    @Autowired
    private final JwtUtils jwtUtils;

    private final UserDetailsService userDetailsService;

    public JwtAuthFilter(JwtUtils jwtUtils, UserDetailsService userDetailsService) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        System.out.println(">>> JwtAuthFilter processing request: " + request.getRequestURI());
        String token = extractToken(request);
        if (!StringUtils.hasText(token) && request.getRequestURI().startsWith("/ws")) {
            token = request.getParameter("token");
            if (StringUtils.hasText(token)) {
                System.out.println(">>> JwtAuthFilter extracted token from query parameter");
            }
        }

        System.out.println(">>> JwtAuthFilter final token: " + (StringUtils.hasText(token) ? "Present" : "Absent"));

        if (StringUtils.hasText(token) && jwtUtils.validateToken(token)) {
            System.out.println(">>> JwtAuthFilter: Token present and valid, attempting to set auth.");
            try {
                String username = jwtUtils.extractUsername(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
                System.out.println(">>> JwtAuthFilter: Authentication set in SecurityContext for: " + username);
            } catch (Exception e) {
                System.err.println(">>> JwtAuthFilter: Error during authentication setup: " + e.getMessage());
                SecurityContextHolder.clearContext();
            }
        } else if (StringUtils.hasText(token)) {
            System.out.println(">>> JwtAuthFilter: Token present but INVALID.");
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            System.out.println(">>> JwtAuthFilter extracted token from Header");
            return header.substring(7);
        }
        return null;
    }
}
