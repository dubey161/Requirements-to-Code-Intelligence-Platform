package com.engineeringproductivity.platform.auth.application;

import com.engineeringproductivity.platform.auth.api.AdminCreateUserRequest;
import com.engineeringproductivity.platform.auth.api.AuthResponse;
import com.engineeringproductivity.platform.auth.api.LoginRequest;
import com.engineeringproductivity.platform.auth.api.RegisterRequest;
import com.engineeringproductivity.platform.auth.domain.Role;
import com.engineeringproductivity.platform.auth.domain.User;
import com.engineeringproductivity.platform.auth.domain.UserRepository;
import com.engineeringproductivity.platform.common.api.ResourceConflictException;
import com.engineeringproductivity.platform.common.api.ResourceNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AuthService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    // ── UserDetailsService (Spring Security hook) ─────────────────────────────

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    // ── Public auth ───────────────────────────────────────────────────────────

    /** Self-registration always creates a DEVELOPER account. */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResourceConflictException("Email already registered: " + request.email());
        }
        User user = User.create(
                request.email(),
                passwordEncoder.encode(request.password()),
                Role.DEVELOPER
        );
        userRepository.save(user);
        return issueTokens(user);
    }

    /** Validates credentials manually — avoids circular dependency with AuthenticationManager. */
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!user.isActive()) {
            throw new DisabledException("Account is deactivated. Contact your administrator.");
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return issueTokens(user);
    }

    /** Issues a fresh token pair using a valid refresh token. */
    public AuthResponse refresh(String refreshToken) {
        String email = jwtService.extractUsername(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (!jwtService.isTokenValid(refreshToken, user)) {
            throw new BadCredentialsException("Refresh token is expired or invalid");
        }
        return issueTokens(user);
    }

    // ── Admin user management ─────────────────────────────────────────────────

    @Transactional
    public User adminCreateUser(AdminCreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResourceConflictException("Email already registered: " + request.email());
        }
        User user = User.create(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.role()
        );
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<User> listAllUsers() {
        return userRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public User setUserActive(UUID userId, boolean active) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        if (active) user.activate(); else user.deactivate();
        return userRepository.save(user);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private AuthResponse issueTokens(User user) {
        return new AuthResponse(
                jwtService.generateAccessToken(user),
                jwtService.generateRefreshToken(user),
                user.getRole().name(),
                user.getId()
        );
    }
}
