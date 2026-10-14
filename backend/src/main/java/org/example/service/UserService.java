package org.example.service;

import org.example.dto.PageResponse;
import org.example.dto.UserResponse;
import org.example.enums.Role;
import org.springframework.data.domain.Pageable;

/**
 * Administrative operations on user accounts.
 */
public interface UserService {

    PageResponse<UserResponse> getUsers(Pageable pageable);

    UserResponse updateRole(Long userId, Role role, String actingUserEmail);
}
