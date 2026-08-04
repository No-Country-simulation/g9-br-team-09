package br.com.g9.energiai.backend.mapper;

import br.com.g9.energiai.backend.dto.response.AuthenticatedUserResponse;
import br.com.g9.energiai.backend.dto.response.UserRegistrationResponse;
import br.com.g9.energiai.backend.entity.AppUser;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserRegistrationResponse toRegistrationResponse(AppUser user) {
        return new UserRegistrationResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }

    public AuthenticatedUserResponse toAuthenticatedUserResponse(AppUser user) {
        return new AuthenticatedUserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
