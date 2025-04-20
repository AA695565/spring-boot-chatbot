package com.example.chatbot.controller;

import com.example.chatbot.service.GeminiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class TestController {

    private final GeminiService geminiService;

    @Autowired
    public TestController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @GetMapping
    public String testGemini() {
        return "Testing Gemini API: " + geminiService.testGeminiApi();
    }
} 