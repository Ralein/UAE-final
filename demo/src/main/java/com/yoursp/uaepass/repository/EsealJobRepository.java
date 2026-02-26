package com.yoursp.uaepass.repository;

import com.yoursp.uaepass.model.entity.EsealJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EsealJobRepository extends JpaRepository<EsealJob, UUID> {

    List<EsealJob> findByRequestedByOrderByCreatedAtDesc(UUID requestedBy);
}
