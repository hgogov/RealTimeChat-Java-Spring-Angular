package com.chatapp.backend.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data // Lombok: Auto-generates getters/setters
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String username;

    @Column(unique = true)
    private String email;

    private String password;
}
