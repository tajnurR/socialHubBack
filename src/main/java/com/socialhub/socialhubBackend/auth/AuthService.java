package com.socialhub.socialhubBackend.auth;

import com.socialhub.socialhubBackend.auth.dto.AuthDtos.AuthResponse;
import com.socialhub.socialhubBackend.auth.dto.AuthDtos.LoginRequest;
import com.socialhub.socialhubBackend.auth.dto.AuthDtos.RegisterRequest;
import com.socialhub.socialhubBackend.auth.dto.AuthDtos.UserProfile;
import com.socialhub.socialhubBackend.auth.token.TokenService;
import com.socialhub.socialhubBackend.auth.token.TokenService.AuthTokens;
import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.common.exception.ResourceNotFoundException;
import com.socialhub.socialhubBackend.config.AppProperties;
import com.socialhub.socialhubBackend.user.context.CurrentUserProvider;
import com.socialhub.socialhubBackend.user.domain.User;
import com.socialhub.socialhubBackend.user.domain.UserRole;
import com.socialhub.socialhubBackend.user.domain.UserStatus;
import com.socialhub.socialhubBackend.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication business logic: registration, login (credential check), and the
 * current profile. Token issuance is delegated to {@link TokenService} (the
 * swappable boundary); password verification uses BCrypt.
 *
 * <p>Self-registration is open and places the new user in the default
 * organization with role {@code MEMBER} (org provisioning is a later concern).
 */
@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final CurrentUserProvider currentUserProvider;
    private final AppProperties appProperties;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            CurrentUserProvider currentUserProvider,
            AppProperties appProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.currentUserProvider = currentUserProvider;
        this.appProperties = appProperties;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("An account with this email already exists", HttpStatus.CONFLICT);
        }
        User user = new User();
        user.setOrganizationId(appProperties.tenant().defaultOrganizationId());
        user.setEmail(email);
        user.setDisplayName(request.displayName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.MEMBER);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        return toAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email).orElse(null);
        // Uniform error to avoid leaking which emails exist.
        if (user == null
                || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException("Invalid email or password", HttpStatus.UNAUTHORIZED);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("This account is not active", HttpStatus.FORBIDDEN);
        }
        return toAuthResponse(user);
    }

    /** Current authenticated user's profile (resolved via CurrentUserProvider). */
    public UserProfile currentProfile() {
        Long userId = currentUserProvider.currentUser().userId();
        return userRepository.findById(userId)
                .map(this::toProfile)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private AuthResponse toAuthResponse(User user) {
        AuthTokens tokens = tokenService.issue(user);
        return new AuthResponse(
                tokens.accessToken(), tokens.refreshToken(), tokens.expiresInSeconds(), toProfile(user));
    }

    private UserProfile toProfile(User user) {
        return new UserProfile(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                user.getOrganizationId());
    }
}
