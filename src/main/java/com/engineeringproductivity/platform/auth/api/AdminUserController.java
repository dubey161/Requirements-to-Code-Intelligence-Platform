package com.engineeringproductivity.platform.auth.api;

import com.engineeringproductivity.platform.auth.application.AuthService;
import com.engineeringproductivity.platform.auth.domain.Role;
import com.engineeringproductivity.platform.auth.domain.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Admin-only user management.
 * Path-level security is already enforced in SecurityConfig (/api/v1/admin/** → ADMIN).
 * @PreAuthorize adds method-level enforcement as a defence-in-depth measure.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AuthService authService;

    public AdminUserController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(@Valid @RequestBody AdminCreateUserRequest request) {
        return UserView.from(authService.adminCreateUser(request));
    }

    @GetMapping
    public List<UserView> listAll() {
        return authService.listAllUsers().stream().map(UserView::from).toList();
    }

    @PutMapping("/{id}/deactivate")
    public UserView deactivate(@PathVariable UUID id) {
        return UserView.from(authService.setUserActive(id, false));
    }

    @PutMapping("/{id}/activate")
    public UserView activate(@PathVariable UUID id) {
        return UserView.from(authService.setUserActive(id, true));
    }

    // ── Response projection (never expose hashed password) ───────────────────

    public record UserView(UUID id, String email, Role role, boolean active, Instant createdAt) {
        static UserView from(User u) {
            return new UserView(u.getId(), u.getEmail(), u.getRole(), u.isActive(), u.getCreatedAt());
        }
    }
}
