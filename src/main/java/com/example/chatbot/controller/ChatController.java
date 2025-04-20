package com.example.chatbot.controller;

import com.example.chatbot.service.NlpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final NlpService nlpService;

    @Autowired
    public ChatController(NlpService nlpService) {
        this.nlpService = nlpService;
    }

    @PostMapping
    public String handleMessage(@RequestBody Map<String, String> payload) {
        String message = payload.get("message");
        if (message == null) {
            return "No message provided";
        }
        return nlpService.process(message);
    }
} 