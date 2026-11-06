package com.example.odata.repository;
import com.example.odata.entity.ContactEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContactRepository
        extends JpaRepository<ContactEntity, Long> {

    Optional<ContactEntity> findBySalesforceContactId(
        String salesforceContactId
    );
}

