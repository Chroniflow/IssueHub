package com.simpissuehub.application;
import cn.dev33.satoken.stp.StpUtil;
import com.simpissuehub.config.ApiTokenFilter;
import com.simpissuehub.domain.User;
import com.simpissuehub.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
@Service public class CurrentUserService {
    private final UserRepository users; private final HttpServletRequest request;
    public CurrentUserService(UserRepository users, HttpServletRequest request) { this.users = users; this.request = request; }
    public User require() {
        Object apiUserId = request.getAttribute(ApiTokenFilter.USER_ID);
        boolean sessionRequest = apiUserId == null && StpUtil.isLogin();
        Long id = apiUserId instanceof Long value ? value : sessionRequest ? StpUtil.getLoginIdAsLong() : null;
        if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        User user = users.findById(id).filter(u -> u.enabled).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在或已停用"));
        if (sessionRequest) StpUtil.updateLastActiveToNow();
        return user;
    }

    /** 返回当前登录用户；匿名访问时返回 {@code null}，不会抛出未认证异常。 */
    public User optional() {
        Object apiUserId = request.getAttribute(ApiTokenFilter.USER_ID);
        boolean sessionRequest = apiUserId == null && StpUtil.isLogin();
        Long id = apiUserId instanceof Long value ? value : sessionRequest ? StpUtil.getLoginIdAsLong() : null;
        if (id == null) return null;
        return users.findById(id).filter(u -> u.enabled).orElse(null);
    }
}
