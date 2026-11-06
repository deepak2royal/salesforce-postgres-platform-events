package com.example.odata.consumer;

import com.example.odata.dto.ContactEventDTO;
import com.example.odata.service.ContactService;

import org.springframework.stereotype.Component;

@Component
public class ContactEventConsumer {

    private final ContactService contactService;

    public ContactEventConsumer(
            ContactService contactService) {

        this.contactService = contactService;
    }

    // Called by the Pub/Sub API adapter
    // after decoding the Avro event payload.

    public void handleContactEvent(
            ContactEventDTO event) {

        try {

            contactService.processContact(event);

            System.out.println(
                "Contact synchronized successfully: "
                + event.getContactId()
            );

        } catch (Exception ex) {

            System.err.println(
                "Contact synchronization failed: "
                + ex.getMessage()
            );

            throw ex;
        }
    }
}
