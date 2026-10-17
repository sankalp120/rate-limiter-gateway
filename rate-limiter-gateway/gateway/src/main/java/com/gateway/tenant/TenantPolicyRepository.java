package com.gateway.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantPolicyRepository extends JpaRepository<TenantPolicy, String> {
}
