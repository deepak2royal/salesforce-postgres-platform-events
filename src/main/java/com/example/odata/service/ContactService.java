package com.example.odata.service;


import com.example.odata.dto.ContactEventDTO;
import com.example.odata.entity.ContactEntity;
import com.example.odata.repository.ContactRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ContactService {

    private final ContactRepository repository;

    public ContactService(ContactRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void processContact(ContactEventDTO event) {

        if ("DELETE".equalsIgnoreCase(event.getOperation())) {

            repository.findBySalesforceContactId(
                event.getContactId()
            ).ifPresent(repository::delete);

            return;
        }

        Optional<ContactEntity> existing =
            repository.findBySalesforceContactId(
                event.getContactId()
            );

        ContactEntity contact =
            existing.orElseGet(ContactEntity::new);

        contact.setSalesforceContactId(
            event.getContactId()
        );

        contact.setFirstName(event.getFirstName());

        contact.setLastName(event.getLastName());

        contact.setEmail(event.getEmail());

        contact.setPhone(event.getPhone());

        contact.setOperation(event.getOperation());

        contact.setSourceSystem(event.getSourceSystem());

        repository.save(contact);
    }

    public Optional<ContactEntity> getContact(String contactId) {

    return repository.findBySalesforceContactId(contactId);
}
}

