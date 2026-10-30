package com.ai.mcp.server.repository;

import com.ai.mcp.server.domain.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, Long> {
}
