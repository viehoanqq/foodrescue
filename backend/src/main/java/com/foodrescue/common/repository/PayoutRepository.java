package com.foodrescue.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.foodrescue.common.entity.Payout;

public interface PayoutRepository extends JpaRepository<Payout, Long> {}
