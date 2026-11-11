package com.borrowbuddy.repository;

import com.borrowbuddy.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUstlAccountIgnoreCase(String ustlAccount);

    Optional<User> findByStudentId(String studentId);
}
