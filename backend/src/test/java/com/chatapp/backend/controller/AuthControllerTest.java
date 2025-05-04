package com.chatapp.backend.controller;

import com.chatapp.backend.config.DataInitializer;
import com.chatapp.backend.config.TestControllerConfiguration;
import com.chatapp.backend.model.ChatRoom;
import com.chatapp.backend.model.User;
import com.chatapp.backend.repository.ChatRoomRepository;
import com.chatapp.backend.service.ChatRoomService;
import com.chatapp.backend.service.CustomUserDetailsService;
import com.chatapp.backend.repository.UserRepository;
import com.chatapp.backend.utils.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import org.springframework.transaction.annotation.Transactional;


import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
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

    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private ChatRoomService chatRoomService;

    // Test Data
    private User registrationUser;
    private User savedUser;
    private ChatRoom generalRoom;

    @BeforeEach
    void setUp() {
        Mockito.reset(customUserDetailsService, passwordEncoder, authenticationManager,
                jwtUtils, userRepository, chatRoomRepository, chatRoomService);

        registrationUser = new User();
        registrationUser.setUsername("regUser");
        registrationUser.setEmail("reg@test.com");
        registrationUser.setPassword("password");

        savedUser = new User();
        savedUser.setId(1L);
        savedUser.setUsername("regUser");
        savedUser.setEmail("reg@test.com");
        savedUser.setPassword("encodedPassword");

        generalRoom = new ChatRoom();
        generalRoom.setId(99L);
        generalRoom.setName(DataInitializer.GENERAL_ROOM_NAME);
    }

    @Test
    void register_whenValidUser_shouldReturnOkAndSaveUser() throws Exception {
        given(passwordEncoder.encode(registrationUser.getPassword())).willReturn("encodedPassword");

        given(userRepository.saveAndFlush(any(User.class))).willReturn(savedUser);

        given(chatRoomRepository.findByName(DataInitializer.GENERAL_ROOM_NAME))
                .willReturn(Optional.of(generalRoom));

        given(userRepository.findById(savedUser.getId()))
                .willReturn(Optional.of(savedUser));

        doNothing().when(chatRoomService).addUserToRoom(eq(generalRoom.getId()), any(User.class));


        ResultActions response = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registrationUser)));

        try {
            response.andExpect(status().isOk())
                    .andExpect(content().string("User registered!"));
        } catch (AssertionError e) {
            System.err.println("Failure in: register_whenValidUser_shouldReturnOkAndSaveUser");
            response.andDo(print());
            throw e;
        }

        verify(passwordEncoder, times(1)).encode(registrationUser.getPassword());
        verify(userRepository, times(1)).saveAndFlush(any(User.class));
        verify(chatRoomRepository, times(1)).findByName(DataInitializer.GENERAL_ROOM_NAME);
        verify(userRepository, times(1)).findById(savedUser.getId());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        verify(chatRoomService, times(1)).addUserToRoom(eq(generalRoom.getId()), userCaptor.capture());
        assertThat(userCaptor.getValue().getId()).isEqualTo(savedUser.getId());
    }

}