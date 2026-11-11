package com.borrowbuddy.repository;

import com.borrowbuddy.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Long> {
}
