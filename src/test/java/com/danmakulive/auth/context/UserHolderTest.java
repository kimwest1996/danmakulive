package com.danmakulive.auth.context;

import com.danmakulive.auth.model.dto.UserDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserHolderTest {

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void saveAndGetUser() {
        UserDTO user = new UserDTO("user-1", "Test", "");
        UserHolder.saveUser(user);
        assertEquals("user-1", UserHolder.getUser().getId());
    }

    @Test
    void removeUserClears() {
        UserHolder.saveUser(new UserDTO("user-1", "Test", ""));
        UserHolder.removeUser();
        assertNull(UserHolder.getUser());
    }

    @Test
    void threadsAreIsolated() throws Exception {
        UserHolder.saveUser(new UserDTO("main", "Main", ""));

        Thread other = new Thread(() -> {
            assertNull(UserHolder.getUser());
            UserHolder.saveUser(new UserDTO("other", "Other", ""));
            UserHolder.removeUser();
        });
        other.start();
        other.join();

        assertEquals("main", UserHolder.getUser().getId());
    }
}
