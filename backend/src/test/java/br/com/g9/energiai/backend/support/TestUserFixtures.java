package br.com.g9.energiai.backend.support;

import br.com.g9.energiai.backend.entity.AppUser;
import br.com.g9.energiai.backend.enums.UserRole;

public final class TestUserFixtures {

    private TestUserFixtures() {
    }

    public static AppUser nonPersistedActiveUser(long id) {
        return AppUser.builder()
                .id(id)
                .name("Non Persisted Test User")
                .email("non-persisted-user-" + id + "@example.test")
                .passwordHash("test-password-hash")
                .role(UserRole.USER)
                .active(true)
                .build();
    }
}
