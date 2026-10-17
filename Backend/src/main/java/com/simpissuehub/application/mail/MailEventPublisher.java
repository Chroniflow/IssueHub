package com.simpissuehub.application.mail;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class MailEventPublisher {
    private final ApplicationEventPublisher publisher;
    public MailEventPublisher(ApplicationEventPublisher publisher) { this.publisher = publisher; }
    public void publish(MailEvent event) { publisher.publishEvent(event); }
}
