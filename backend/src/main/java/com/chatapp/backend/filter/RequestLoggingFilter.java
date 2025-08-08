package com.chatapp.backend.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Enumeration;

@Component
@Order(1)
public class RequestLoggingFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        logger.info("====================== INCOMING REQUEST ======================");
        logger.info("Request Method: " + httpRequest.getMethod());
        logger.info("Request URI: " + httpRequest.getRequestURI());

        Enumeration<String> headerNames = httpRequest.getHeaderNames();
        if (headerNames != null) {
            logger.info("--- Headers ---");
            while (headerNames.hasMoreElements()) {
                String header = headerNames.nextElement();
                logger.info(String.format("%s: %s", header, httpRequest.getHeader(header)));
            }
            logger.info("--- End Headers ---");
        }

        logger.info("==================== END INCOMING REQUEST ====================");

        chain.doFilter(request, response);
    }
}