package com.simpissuehub.application;

import com.simpissuehub.domain.Issue;
import com.simpissuehub.domain.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthorizationService {
    public User requireAdmin(User user) {
        if (user == null || user.role != User.Role.ADMIN) throw forbidden("只有管理员可以执行此操作");
        return user;
    }

    public User requireStaff(User user) {
        if (user == null || user.role == User.Role.USER) throw forbidden("只有工作人员或管理员可以执行此操作");
        return user;
    }

    public boolean canManageIssue(Issue issue, User user) {
        return issue != null && user != null && (issue.author.id.equals(user.id) || user.role != User.Role.USER);
    }

    public boolean canCloseIssue(Issue issue, User user) {
        return issue != null && user != null && (issue.author.id.equals(user.id) || user.role != User.Role.USER);
    }

    private ResponseStatusException forbidden(String message) { return new ResponseStatusException(HttpStatus.FORBIDDEN, message); }
}
