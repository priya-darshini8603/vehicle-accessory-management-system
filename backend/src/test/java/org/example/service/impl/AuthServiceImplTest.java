package org.example.service.impl;

import org.example.dto.AuthResponse;
import org.example.dto.LoginRequest;
import org.example.dto.RegisterRequest;
import org.example.dto.UserResponse;
import org.example.entity.User;
import org.example.enums.Role;
import org.example.exception.DuplicateResourceException;
import org.example.mapper.UserMapper;
import org.example.repository.UserRepository;
import org.example.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(userRepository, passwordEncoder, authenticationManager, jwtService, new UserMapper());
    }

    @Test
    void registerAlwaysCreatesCustomerWithNormalizedEmailAndHashedPassword() {
        when(userRepository.existsByEmail("asha@mercedes-benz.com")).thenReturn(false);
        when(passwordEncoder.encode("a-long-password")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.register(new RegisterRequest(
                " Asha ", " Asha@Mercedes-Benz.com ", "a-long-password", ""));

        assertThat(response.email()).isEqualTo("asha@mercedes-benz.com");
        assertThat(response.fullName()).isEqualTo("Asha");
        assertThat(response.role()).isEqualTo(Role.CUSTOMER);
        assertThat(response.phoneNumber()).isNull();
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("asha@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                new RegisterRequest("Asha", "asha@example.com", "a-long-password", null)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginReturnsBearerTokenAndUserDetails() {
        UserDetails principal = org.springframework.security.core.userdetails.User
                .withUsername("asha@example.com").password("hashed").roles("CUSTOMER").build();
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        User user = new User();
        user.setEmail("asha@example.com");
        user.setRole(Role.CUSTOMER);
        when(userRepository.findByEmail("asha@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(principal)).thenReturn("jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(1_800_000L);

        AuthResponse response = service.login(new LoginRequest("ASHA@example.com", "a-long-password"));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(1800);
        assertThat(response.user().role()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void loginPropagatesBadCredentials() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> service.login(new LoginRequest("asha@example.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
