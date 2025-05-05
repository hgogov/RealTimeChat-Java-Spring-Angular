package com.chatapp.backend.repository;

import com.chatapp.backend.model.ChatRoom;
import com.chatapp.backend.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.HashSet;
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
    private ChatRoom roomPublicA;
    private ChatRoom roomPublicB;
    private ChatRoom roomPublicC;
    private ChatRoom roomPrivateD;

    @BeforeEach
    void setUpDatabase() {
        assertNotNull(entityManager, "EntityManager should be injected");
        assertNotNull(chatRoomRepository, "ChatRoomRepository should be injected");
        assertNotNull(userRepository, "UserRepository should be injected");
        System.out.println("--- Starting @BeforeEach Setup (Repo Test) ---");


        // 1. Create Users
        User u1 = new User();
        u1.setUsername("userRepo1");
        u1.setEmail("user1@test.com");
        u1.setPassword("pass");
        u1.setChatRooms(new HashSet<>());
        User u2 = new User();
        u2.setUsername("userRepo2");
        u2.setEmail("user2@test.com");
        u2.setPassword("pass");
        u2.setChatRooms(new HashSet<>());
        User u3 = new User();
        u3.setUsername("userRepo3");
        u3.setEmail("user3@test.com");
        u3.setPassword("pass");
        u3.setChatRooms(new HashSet<>());

        // 2. Persist Users
        user1 = entityManager.persistAndFlush(u1);
        user2 = entityManager.persistAndFlush(u2);
        user3 = entityManager.persistAndFlush(u3);
        System.out.println("Persisted Users: user1=" + user1.getId() + ", user2=" + user2.getId() + ", user3=" + user3.getId());


        // 3. Create Rooms with varying public status
        ChatRoom rA = ChatRoom.builder().name("Repo Public A").createdBy(user1).isPublic(true).members(new HashSet<>()).build();
        ChatRoom rB = ChatRoom.builder().name("Repo Public B").createdBy(user1).isPublic(true).members(new HashSet<>()).build();
        ChatRoom rC = ChatRoom.builder().name("Repo Public C").createdBy(user2).isPublic(true).members(new HashSet<>()).build();
        ChatRoom rD = ChatRoom.builder().name("Repo Private D").createdBy(user1).isPublic(false).members(new HashSet<>()).build();

        // 4. Persist Rooms
        roomPublicA = entityManager.persistAndFlush(rA);
        roomPublicB = entityManager.persistAndFlush(rB);
        roomPublicC = entityManager.persistAndFlush(rC);
        roomPrivateD = entityManager.persistAndFlush(rD);
        System.out.println("Persisted Rooms: A=" + roomPublicA.getId() + ", B=" + roomPublicB.getId() + ", C=" + roomPublicC.getId() + ", D=" + roomPrivateD.getId());


        // 5. Establish Memberships
        User managedUser1 = userRepository.findById(user1.getId()).orElseThrow();
        User managedUser2 = userRepository.findById(user2.getId()).orElseThrow();

        ChatRoom managedRoomA = chatRoomRepository.findById(roomPublicA.getId()).orElseThrow();
        ChatRoom managedRoomB = chatRoomRepository.findById(roomPublicB.getId()).orElseThrow();
        ChatRoom managedRoomD = chatRoomRepository.findById(roomPrivateD.getId()).orElseThrow();

        // User1 joins Public A, Public B, Private D
        managedUser1.getChatRooms().add(managedRoomA);
        managedUser1.getChatRooms().add(managedRoomB);
        managedUser1.getChatRooms().add(managedRoomD);
        System.out.println("Adding rooms A, B, D to user1");

        // User2 joins Public A ONLY
        managedUser2.getChatRooms().add(managedRoomA);
        System.out.println("Adding room A to user2");


        // 6. Persist the User entities (owning side)
        entityManager.persist(managedUser1);
        entityManager.persist(managedUser2);
        entityManager.flush();

        System.out.println("Flushed user memberships.");


        // --- Verification Step ---
        entityManager.clear();
        System.out.println("Persistence Context Cleared. Verifying setup...");


        ChatRoom fetchedRoomA = chatRoomRepository.findById(roomPublicA.getId()).orElseThrow();
        assertThat(fetchedRoomA.getMembers()).as("Verify members in Room A after setup")
                .hasSize(2)
                .extracting(User::getUsername)
                .containsExactlyInAnyOrder("userRepo1", "userRepo2");
        System.out.println("Verified Room A members.");

        ChatRoom fetchedRoomB = chatRoomRepository.findById(roomPublicB.getId()).orElseThrow();
        assertThat(fetchedRoomB.getMembers()).as("Verify members in Room B after setup")
                .hasSize(1)
                .extracting(User::getUsername)
                .containsExactly("userRepo1");
        System.out.println("Verified Room B members.");

        ChatRoom fetchedRoomC = chatRoomRepository.findById(roomPublicC.getId()).orElseThrow();

        assertThat(fetchedRoomC.getMembers()).as("Verify members in Room C after setup ")
                .isEmpty();
        System.out.println("Verified Room C members (expected empty).");

        ChatRoom fetchedRoomD = chatRoomRepository.findById(roomPrivateD.getId()).orElseThrow();
        assertThat(fetchedRoomD.getMembers()).as("Verify members in Room D after setup")
                .hasSize(1)
                .extracting(User::getUsername)
                .containsExactly("userRepo1");
        System.out.println("Verified Room D members.");


        // Verify the membership check query
        boolean user1InRoomAQuery = userRepository.existsByUsernameAndChatRooms_Id("userRepo1", roomPublicA.getId());
        assertThat(user1InRoomAQuery).as("Verify user1 exists in roomA via query").isTrue();
        boolean user2InRoomAQuery = userRepository.existsByUsernameAndChatRooms_Id("userRepo2", roomPublicA.getId());
        assertThat(user2InRoomAQuery).as("Verify user2 exists in roomA via query").isTrue();
        boolean user2InRoomBQuery = userRepository.existsByUsernameAndChatRooms_Id("userRepo2", roomPublicB.getId());
        assertThat(user2InRoomBQuery).as("Verify user2 NOT in roomB via query").isFalse();
        System.out.println("Verified membership queries.");

        System.out.println("--- @BeforeEach Setup Verified & Completed ---");
    }


    @Test
    void findByName_whenExists_shouldReturnRoom() {
        Optional<ChatRoom> found = chatRoomRepository.findByName("Repo Public A");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Repo Public A");
        assertThat(found.get().getId()).isEqualTo(roomPublicA.getId());
    }

    @Test
    void findByName_whenNotExists_shouldReturnEmpty() {
        Optional<ChatRoom> found = chatRoomRepository.findByName("NonExistent");
        assertThat(found).isEmpty();
    }

    @Test
    void findChatRoomsByUserId_whenUserIsInRooms_shouldReturnRooms() {
        List<ChatRoom> user1Rooms = chatRoomRepository.findChatRoomsByUserId(user1.getId());
        assertThat(user1Rooms)
                .hasSize(3)
                .extracting(ChatRoom::getName)
                .containsExactlyInAnyOrder("Repo Public A", "Repo Public B", "Repo Private D");

        List<ChatRoom> user2Rooms = chatRoomRepository.findChatRoomsByUserId(user2.getId());
        assertThat(user2Rooms)
                .hasSize(1)
                .extracting(ChatRoom::getName)
                .containsExactly("Repo Public A");
    }

    @Test
    void findChatRoomsByUserId_whenUserNotInAnyRoom_shouldReturnEmptyList() {
        List<ChatRoom> user3Rooms = chatRoomRepository.findChatRoomsByUserId(user3.getId());
        assertThat(user3Rooms).isEmpty();
    }


    @Test
    void existsByUsernameAndChatRooms_Id_whenMember_shouldReturnTrue() {
        boolean isMember1 = userRepository.existsByUsernameAndChatRooms_Id("userRepo1", roomPublicA.getId());
        assertThat(isMember1).as("user1 in roomPublicA").isTrue();

        boolean isMember2 = userRepository.existsByUsernameAndChatRooms_Id("userRepo2", roomPublicA.getId());
        assertThat(isMember2).as("user2 in roomPublicA").isTrue();
    }

    @Test
    void existsByUsernameAndChatRooms_Id_whenNotMember_shouldReturnFalse() {
        boolean isMember = userRepository.existsByUsernameAndChatRooms_Id("userRepo2", roomPublicB.getId());
        assertThat(isMember).as("user2 NOT in roomPublicB").isFalse();

        boolean isMember3 = userRepository.existsByUsernameAndChatRooms_Id("userRepo3", roomPublicA.getId());
        assertThat(isMember3).as("user3 NOT in roomPublicA").isFalse();
    }

    @Test
    void existsByUsernameAndChatRooms_Id_whenUserOrRoomNotExist_shouldReturnFalse() {
        boolean isMember = userRepository.existsByUsernameAndChatRooms_Id("nonexistentuser", roomPublicA.getId());
        assertThat(isMember).as("non-existent user").isFalse();

        isMember = userRepository.existsByUsernameAndChatRooms_Id("userRepo1", 999L); // Non-existent room ID
        assertThat(isMember).as("non-existent room").isFalse();
    }

    @Test
    void findDiscoverableRoomsForUser_shouldReturnPublicRoomsUserIsNotMemberOf() {
        List<ChatRoom> discoverableForUser1 = chatRoomRepository.findDiscoverableRoomsForUser(user1.getId());
        assertThat(discoverableForUser1)
                .as("Discoverable for User 1")
                .hasSize(1)
                .extracting(ChatRoom::getName)
                .containsExactly("Repo Public C");

        List<ChatRoom> discoverableForUser2 = chatRoomRepository.findDiscoverableRoomsForUser(user2.getId());
        assertThat(discoverableForUser2)
                .as("Discoverable for User 2")
                .hasSize(2)
                .extracting(ChatRoom::getName)
                .containsExactlyInAnyOrder("Repo Public B", "Repo Public C");

        List<ChatRoom> discoverableForUser3 = chatRoomRepository.findDiscoverableRoomsForUser(user3.getId());
        assertThat(discoverableForUser3)
                .as("Discoverable for User 3")
                .hasSize(3)
                .extracting(ChatRoom::getName)
                .containsExactlyInAnyOrder("Repo Public A", "Repo Public B", "Repo Public C");
    }
}