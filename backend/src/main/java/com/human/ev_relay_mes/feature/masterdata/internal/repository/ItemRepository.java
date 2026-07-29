package com.human.ev_relay_mes.feature.masterdata.internal.repository;

import com.human.ev_relay_mes.feature.masterdata.api.Item;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, String> {
}
