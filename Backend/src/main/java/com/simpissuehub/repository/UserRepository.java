package com.simpissuehub.repository;
import com.simpissuehub.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Sort;
import java.util.Optional;
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsernameOrEmail(String username, String email);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameOrEmail(String username, String email);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
    default java.util.List<User> findForTui() { return findAll(Sort.by(Sort.Direction.ASC, "id")); }
    long countByEnabledTrue();
}
