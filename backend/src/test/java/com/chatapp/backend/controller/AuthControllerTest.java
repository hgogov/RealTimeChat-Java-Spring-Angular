package com.chatapp.backend.controller;

import com.chatapp.backend.config.TestControllerConfiguration;
import com.chatapp.backend.model.User;
import com.chatapp.backend.service.CustomUserDetailsService;
import com.chatapp.backend.repository.UserRepository;
import com.chatapp.backend.utils.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestControllerConfiguration.class)
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserRepository userRepository;

    // Test Data
    private User registrationUser;
    private User savedUser;

    @BeforeEach
    void setUp() {

        Mockito.reset(customUserDetailsService, userRepository);

        registrationUser = new User();
        registrationUser.setUsername("testuser");
        registrationUser.setEmail("test@test.com");
        registrationUser.setPassword("password");

        savedUser = new User();
        savedUser.setId(1L);
        savedUser.setUsername("testuser");
        savedUser.setEmail("test@test.com");
        savedUser.setPassword("encodedPassword");
    }

    @Test
    void register_whenValidUser_shouldReturnOkAndSaveUser() throws Exception {
        // Arrange
        given(passwordEncoder.encode("password")).willReturn("encodedPassword");
        given(userRepository.saveAndFlush(any(User.class))).willReturn(savedUser);

        // Act
        ResultActions response = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registrationUser)));

        // Assert
        try {
            response.andExpect(status().isOk())
                    .andExpect(content().string("User registered!"));
        } catch (AssertionError e) {
            response.andDo(print());
            throw e;
        }

        verify(passwordEncoder, times(1)).encode("password");
        verify(userRepository, times(1)).saveAndFlush(any(User.class));
    }

}