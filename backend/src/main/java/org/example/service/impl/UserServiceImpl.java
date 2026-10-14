package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.dto.PageResponse;
import org.example.dto.UserResponse;
import org.example.entity.User;
import org.example.enums.Role;
import org.example.exception.BusinessRuleException;
import org.example.exception.ResourceNotFoundException;
import org.example.mapper.UserMapper;
import org.example.repository.UserRepository;
import org.example.service.UserService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getUsers(Pageable pageable) {
        return PageResponse.from(userRepository.findAll(pageable), userMapper::toResponse);
    }

    @Override
    @Transactional
    public UserResponse updateRole(Long userId, Role role, String actingUserEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));

        // Prevents an admin from locking themselves (and possibly everyone) out of the admin API.
        if (user.getEmail().equalsIgnoreCase(actingUserEmail) && role != Role.ADMIN) {
            throw new BusinessRuleException("Administrators cannot remove their own ADMIN role");
        }

        user.setRole(role);
        return userMapper.toResponse(user);
    }
}
