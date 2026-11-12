package com.borrowbuddy.repository;

import com.borrowbuddy.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop15ByOrderByIdDesc();
}
