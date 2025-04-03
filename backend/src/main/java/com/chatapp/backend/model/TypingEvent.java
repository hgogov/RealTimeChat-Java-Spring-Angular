package com.chatapp.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@NoArgsConstructor
public class TypingEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = -7544632781802423532L;

    private String roomId;
    private String username;
    private boolean typing;
}
