package org.example.service.impl;

import java.util.Locale;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.AuthResponse;
import org.example.dto.LoginRequest;
import org.example.dto.RegisterRequest;
import org.example.dto.UserResponse;
import org.example.entity.User;
import org.example.enums.Role;
import org.example.exception.DuplicateResourceException;
import org.example.exception.InvalidRefreshTokenException;
import org.example.mapper.UserMapper;
import org.example.repository.UserRepository;
import org.example.service.AuthService;
import org.example.service.JwtService;
import org.example.service.RefreshTokenService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserDetailsService userDetailsService;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {

        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            log.warn("Registration rejected because the email is already registered");
            throw new DuplicateResourceException("Email is already registered");
        }

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setPhoneNumber(blankToNull(request.phoneNumber()));

        // Self-registration never grants ADMIN.
        user.setRole(Role.CUSTOMER);

        User savedUser = userRepository.save(user);

        log.info(
                "Registered user with id {} and role {}",
                savedUser.getId(),
                savedUser.getRole());

        return userMapper.toResponse(savedUser);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {

        String email = normalizeEmail(request.email());

        Authentication authentication;

        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            email,
                            request.password()));
        } catch (AuthenticationException exception) {
            log.warn("Login rejected due to invalid credentials");
            throw exception;
        }

        Object principal = authentication.getPrincipal();

        if (!(principal instanceof UserDetails userDetails)) {
            throw new BadCredentialsException("Invalid authentication principal");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new BadCredentialsException("Invalid email or password"));

        String accessToken = jwtService.generateToken(userDetails);
        String refreshToken = refreshTokenService.createFor(user);

        log.info("User login succeeded with id {}", user.getId());

        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtService.getExpirationMs() / 1000,
                userMapper.toResponse(user));
    }

    /**
     * noRollbackFor is required: when a replayed token is detected, rotate() revokes the user's
     * whole token family and then throws. Without it the surrounding transaction would roll the
     * revocation back and the replay protection would silently do nothing.
     */
    @Override
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthResponse refresh(String token) {

        RefreshTokenService.RotatedToken rotated =
                refreshTokenService.rotate(token);

        User user = rotated.user();

        UserDetails userDetails =
                userDetailsService.loadUserByUsername(user.getEmail());

        log.info(
                "Refresh token rotated successfully for user id {}",
                user.getId());

        return new AuthResponse(
                jwtService.generateToken(userDetails),
                rotated.refreshToken(),
                "Bearer",
                jwtService.getExpirationMs() / 1000,
                userMapper.toResponse(user));
    }

    @Override
    @Transactional
    public void logout(String token) {

        refreshTokenService.revoke(token);

        log.info("Logout processed");
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}