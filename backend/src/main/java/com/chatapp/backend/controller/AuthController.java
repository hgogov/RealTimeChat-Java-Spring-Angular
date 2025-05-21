package com.chatapp.backend.controller;

import com.chatapp.backend.config.DataInitializer;
import com.chatapp.backend.model.ChatRoom;
import com.chatapp.backend.model.User;
import com.chatapp.backend.repository.ChatRoomRepository;
import com.chatapp.backend.repository.UserRepository;
import com.chatapp.backend.service.ChatRoomService;
import com.chatapp.backend.service.CustomUserDetailsService;
import com.chatapp.backend.utils.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "User Registration and Login")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class); // Add logger

    @Autowired
    private ChatRoomService chatRoomService;
    @Autowired
    private ChatRoomRepository chatRoomRepository;

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

    @Operation(summary = "Register a new user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User registered successfully",
                    content = @Content(mediaType = "text/plain", schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "400", description = "Invalid user data provided"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody User user) {
        log.info("Received registration request for user: {}", user.getUsername());
        try {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            User savedUser = userRepository.saveAndFlush(user);
            log.info("User '{}' (ID: {}) saved successfully.", savedUser.getUsername(), savedUser.getId());

            ChatRoom generalRoom = chatRoomRepository.findByName(DataInitializer.GENERAL_ROOM_NAME)
                    .orElseThrow(() -> {
                        log.error("CRITICAL: '{}' room not found during registration for user {}!", DataInitializer.GENERAL_ROOM_NAME, savedUser.getUsername());
                        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Default chat room not available.");
                    });

            User managedSavedUser = userRepository.findById(savedUser.getId()).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot find newly saved user to join General room")
            );
            managedSavedUser.getChatRooms().add(generalRoom);
            userRepository.save(managedSavedUser);

            log.info("User '{}' automatically joined '{}' room.", savedUser.getUsername(), DataInitializer.GENERAL_ROOM_NAME);

            return ResponseEntity.ok("User registered!");

        } catch (ResponseStatusException e) {
            log.warn("Registration failed for user '{}': {}", user.getUsername(), e.getReason());
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (DataIntegrityViolationException e) {
            log.warn("Registration failed for user '{}' due to data integrity violation.", user.getUsername());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Username or Email already exists.");
        } catch (Exception e) {
            log.error("Unexpected error during registration for user '{}'", user.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Registration failed due to server error.");
        }
    }

    @Operation(summary = "Authenticate a user and receive a JWT")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authentication successful, JWT returned",
                    content = @Content(mediaType = "text/plain", schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody User user) {
        System.out.println(">>> Attempting login for user: " + user.getUsername());
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUsername(), user.getPassword())
            );
            UserDetails userDetails = customUserDetailsService.loadUserByUsername(user.getUsername());
            String token = jwtUtils.generateToken(userDetails);
            return ResponseEntity.ok(token);
        } catch (AuthenticationException e) {
            System.err.println(">>> Authentication failed for user: " + user.getUsername() + " - " + e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        } catch (Exception e) {
            System.err.println(">>> Unexpected error during login for user: " + user.getUsername());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Login failed due to server error");
        }
    }
}
