package com.chatapp.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Data
@NoArgsConstructor
@Table(name = "messages")
public class ChatMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 4060570159599848356L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private String sender;

    @Column(nullable = false)
    private String roomId;

    @Column(nullable = false)
    private Instant timestamp = Instant.now();
}
