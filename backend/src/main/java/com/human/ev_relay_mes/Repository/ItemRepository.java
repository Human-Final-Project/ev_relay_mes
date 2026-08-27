package com.human.ev_relay_mes.Repository;

import com.human.ev_relay_mes.Entity.Item;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, String> {
}
