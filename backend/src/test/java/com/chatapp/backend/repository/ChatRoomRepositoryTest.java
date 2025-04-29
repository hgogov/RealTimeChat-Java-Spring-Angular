package com.chatapp.backend.repository;

import com.chatapp.backend.model.ChatRoom;
import com.chatapp.backend.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
class ChatRoomRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private UserRepository userRepository;

    private User user1;
    private User user2;
    private User user3;
    private ChatRoom room1;
    private ChatRoom room2;

    @BeforeEach
    void setUpDatabase() {
        assertNotNull(entityManager);
        assertNotNull(chatRoomRepository);
        assertNotNull(userRepository);

        // 1. Create Users
        User u1 = new User();
        u1.setUsername("userRepo1");
        u1.setEmail("user1@test.com");
        u1.setPassword("pass");
        User u2 = new User();
        u2.setUsername("userRepo2");
        u2.setEmail("user2@test.com");
        u2.setPassword("pass");
        User u3 = new User();
        u3.setUsername("userRepo3");
        u3.setEmail("user3@test.com");
        u3.setPassword("pass");

        // 2. Persist Users
        user1 = entityManager.persist(u1);
        user2 = entityManager.persist(u2);
        user3 = entityManager.persist(u3);
        entityManager.flush();

        // 3. Create Rooms
        ChatRoom r1 = ChatRoom.builder().name("Repo Room 1").createdBy(user1).build();
        ChatRoom r2 = ChatRoom.builder().name("Repo Room 2").createdBy(user1).build();

        // 4. Persist Rooms
        room1 = entityManager.persist(r1);
        room2 = entityManager.persist(r2);
        entityManager.flush();

        User managedUser1 = userRepository.findById(user1.getId()).orElseThrow();
        User managedUser2 = userRepository.findById(user2.getId()).orElseThrow();

        ChatRoom managedRoom1 = chatRoomRepository.findById(room1.getId()).orElseThrow();
        ChatRoom managedRoom2 = chatRoomRepository.findById(room2.getId()).orElseThrow();

        // Add rooms to the user's collection
        managedUser1.getChatRooms().add(managedRoom1);
        managedUser1.getChatRooms().add(managedRoom2);
        managedUser2.getChatRooms().add(managedRoom1);

        // 5. Persist the User entities
        entityManager.persist(managedUser1);
        entityManager.persist(managedUser2);
        entityManager.flush();

        // Verification Step
        entityManager.clear();

        ChatRoom fetchedRoom1 = chatRoomRepository.findById(room1.getId()).orElseThrow();
        assertThat(fetchedRoom1.getMembers()).as("Verify members in Room 1 after setup")
                .hasSize(2)
                .extracting(User::getUsername)
                .containsExactlyInAnyOrder("userRepo1", "userRepo2");

        ChatRoom fetchedRoom2 = chatRoomRepository.findById(room2.getId()).orElseThrow();
        assertThat(fetchedRoom2.getMembers()).as("Verify members in Room 2 after setup")
                .hasSize(1)
                .extracting(User::getUsername)
                .containsExactly("userRepo1");

        boolean user1InRoom1Query = userRepository.existsByUsernameAndChatRooms_Id("userRepo1", room1.getId());
        assertThat(user1InRoom1Query).as("Verify user1 exists in room1 via query").isTrue();
        boolean user2InRoom1Query = userRepository.existsByUsernameAndChatRooms_Id("userRepo2", room1.getId());
        assertThat(user2InRoom1Query).as("Verify user2 exists in room1 via query").isTrue();
        boolean user2InRoom2Query = userRepository.existsByUsernameAndChatRooms_Id("userRepo2", room2.getId());
        assertThat(user2InRoom2Query).as("Verify user2 NOT in room2 via query").isFalse();
        System.out.println("--- @BeforeEach Setup Verified ---");
    }

    @Test
    void findChatRoomsByUserId_whenUserIsInRooms_shouldReturnRooms() {
        List<ChatRoom> user1Rooms = chatRoomRepository.findChatRoomsByUserId(user1.getId());
        assertThat(user1Rooms)
                .hasSize(2)
                .extracting(ChatRoom::getName)
                .containsExactlyInAnyOrder("Repo Room 1", "Repo Room 2");

        List<ChatRoom> user2Rooms = chatRoomRepository.findChatRoomsByUserId(user2.getId());
        assertThat(user2Rooms)
                .hasSize(1)
                .extracting(ChatRoom::getName)
                .containsExactly("Repo Room 1");
    }

    @Test
    void findChatRoomsByUserId_whenUserNotInAnyRoom_shouldReturnEmptyList() {
        List<ChatRoom> user3Rooms = chatRoomRepository.findChatRoomsByUserId(user3.getId());
        assertThat(user3Rooms).isEmpty();
    }


    @Test
    void existsByUsernameAndChatRooms_Id_whenMember_shouldReturnTrue() {
        boolean isMember1 = userRepository.existsByUsernameAndChatRooms_Id("userRepo1", room1.getId());
        assertThat(isMember1).as("user1 in room1").isTrue();

        boolean isMember2 = userRepository.existsByUsernameAndChatRooms_Id("userRepo2", room1.getId());
        assertThat(isMember2).as("user2 in room1").isTrue();
    }

    @Test
    void existsByUsernameAndChatRooms_Id_whenNotMember_shouldReturnFalse() {
        boolean isMember = userRepository.existsByUsernameAndChatRooms_Id("userRepo2", room2.getId());
        assertThat(isMember).as("user2 NOT in room2").isFalse();
    }

    @Test
    void existsByUsernameAndChatRooms_Id_whenUserOrRoomNotExist_shouldReturnFalse() {
        boolean isMember = userRepository.existsByUsernameAndChatRooms_Id("nonexistentuser", room1.getId());
        assertThat(isMember).as("non-existent user").isFalse();

        isMember = userRepository.existsByUsernameAndChatRooms_Id("userRepo1", 999L);
        assertThat(isMember).as("non-existent room").isFalse();
    }

    @Test
    void findByName_whenExists_shouldReturnRoom() {
        Optional<ChatRoom> found = chatRoomRepository.findByName("Repo Room 1");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Repo Room 1");
        assertThat(found.get().getId()).isEqualTo(room1.getId());
    }

    @Test
    void findByName_whenNotExists_shouldReturnEmpty() {
        Optional<ChatRoom> found = chatRoomRepository.findByName("NonExistent");
        assertThat(found).isEmpty();
    }
}