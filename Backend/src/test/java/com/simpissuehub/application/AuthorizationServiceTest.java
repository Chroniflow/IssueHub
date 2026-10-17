package com.simpissuehub.application;

import com.simpissuehub.domain.Issue;
import com.simpissuehub.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizationServiceTest {
    private final AuthorizationService authorization = new AuthorizationService();

    @Test
    void onlyAdminPassesAdminCheck() {
        User admin = user(User.Role.ADMIN);
        assertSame(admin, authorization.requireAdmin(admin));
        assertThrows(ResponseStatusException.class, () -> authorization.requireAdmin(user(User.Role.STAFF)));
        assertThrows(ResponseStatusException.class, () -> authorization.requireAdmin(user(User.Role.USER)));
        assertThrows(ResponseStatusException.class, () -> authorization.requireAdmin(null));
    }

    @Test
    void staffAndAdminPassStaffCheck() {
        assertEquals(User.Role.STAFF, authorization.requireStaff(user(User.Role.STAFF)).role);
        assertEquals(User.Role.ADMIN, authorization.requireStaff(user(User.Role.ADMIN)).role);
        assertThrows(ResponseStatusException.class, () -> authorization.requireStaff(user(User.Role.USER)));
        assertThrows(ResponseStatusException.class, () -> authorization.requireStaff(null));
    }

    @Test
    void issueManagementAllowsAuthorAndStaffOnly() {
        User author = userWithId(1L, User.Role.USER);
        User other = userWithId(2L, User.Role.USER);
        Issue issue = new Issue();
        issue.author = author;

        assertTrue(authorization.canManageIssue(issue, author));
        assertTrue(authorization.canCloseIssue(issue, author));
        assertTrue(authorization.canManageIssue(issue, user(User.Role.STAFF)));
        assertTrue(authorization.canCloseIssue(issue, user(User.Role.ADMIN)));
        assertFalse(authorization.canManageIssue(issue, other));
        assertFalse(authorization.canCloseIssue(issue, other));
        assertFalse(authorization.canManageIssue(null, author));
        assertFalse(authorization.canCloseIssue(issue, null));
    }

    private static User user(User.Role role) {
        return userWithId(role.ordinal() + 1L, role);
    }

    private static User userWithId(Long id, User.Role role) {
        User user = new User();
        user.id = id;
        user.role = role;
        user.enabled = true;
        return user;
    }
}
