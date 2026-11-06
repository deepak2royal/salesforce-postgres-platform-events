package com.example.odata.controller;

import com.example.odata.dto.ContactEventDTO;
import com.example.odata.service.ContactService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @PostMapping
    public ResponseEntity<String> syncContact(
            @RequestBody ContactEventDTO contact) {

        contactService.processContact(contact);

        return ResponseEntity.ok(
            "Contact synchronized successfully"
        );
    }


    @GetMapping("/{contactId}")
    public ResponseEntity<?> getContact(
            @PathVariable String contactId) {

        return contactService.getContact(contactId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}

