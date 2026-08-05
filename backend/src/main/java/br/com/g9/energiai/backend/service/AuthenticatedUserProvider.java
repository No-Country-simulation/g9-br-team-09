package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.entity.AppUser;

public interface AuthenticatedUserProvider {
    AppUser getCurrentUser();
}
