package com.example.chatbot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.Tokenizer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Service
public class NlpService {

    private final Tokenizer tokenizer;
    private final Map<String, List<String>> responses;
    private final Random random;
    private final GeminiService geminiService;
    
    @Autowired
    public NlpService(GeminiService geminiService) {
        this.tokenizer = SimpleTokenizer.INSTANCE;
        this.random = new Random();
        this.responses = initializeResponses();
        this.geminiService = geminiService;
    }
    
    public String process(String text) {
        // Log the incoming message
        System.out.println("Processing with NLP Service: " + text);
        
        try {
            // Try to get a response from Gemini first
            String geminiResponse = geminiService.generateResponse(text);
            
            // If Gemini gave us a real response (not an error message), return it
            if (!geminiResponse.startsWith("Gemini API is not configured") && 
                !geminiResponse.startsWith("Sorry, I encountered an error") &&
                !geminiResponse.startsWith("This is a placeholder")) {
                return geminiResponse;
            }
            
            // If Gemini failed, fall back to our basic keyword matching
            return processWithKeywordMatching(text);
        } catch (Exception e) {
            System.err.println("Error with Gemini API: " + e.getMessage());
            // Fall back to basic keyword matching if Gemini API fails
            return processWithKeywordMatching(text);
        }
    }
    
    private String processWithKeywordMatching(String text) {
        // Clean and normalize the input
        String cleanedText = text.toLowerCase().trim();
        
        // Check for empty input
        if (cleanedText.isEmpty()) {
            return getRandomResponse("empty");
        }
        
        // Tokenize the input
        String[] tokens = tokenizer.tokenize(cleanedText);
        
        // Check for greetings
        if (containsAny(tokens, "hello", "hi", "hey", "greetings", "howdy")) {
            return getRandomResponse("greeting");
        }
        
        // Check for questions about the bot
        if (containsAny(tokens, "who", "what") && containsAny(tokens, "you", "your", "name")) {
            return getRandomResponse("identity");
        }
        
        // Check for gratitude
        if (containsAny(tokens, "thanks", "thank", "appreciate")) {
            return getRandomResponse("gratitude");
        }
        
        // Check for farewells
        if (containsAny(tokens, "bye", "goodbye", "farewell", "see you")) {
            return getRandomResponse("farewell");
        }
        
        // Check for help requests
        if (containsAny(tokens, "help", "assist", "support")) {
            return getRandomResponse("help");
        }
        
        // Check for weather-related questions
        if (containsAny(tokens, "weather", "temperature", "forecast", "rain", "sunny")) {
            return getRandomResponse("weather");
        }
        
        // Check for time-related questions
        if (containsAny(tokens, "time", "date", "day", "today")) {
            return getRandomResponse("time");
        }
        
        // Check for how-are-you questions
        Pattern howAreYouPattern = Pattern.compile("how\\s+are\\s+you");
        Matcher howAreYouMatcher = howAreYouPattern.matcher(cleanedText);
        if (howAreYouMatcher.find()) {
            return getRandomResponse("how_are_you");
        }
        
        // Default response if no patterns match
        return getRandomResponse("default");
    }
    
    private boolean containsAny(String[] tokens, String... keywords) {
        List<String> tokenList = Arrays.asList(tokens);
        for (String keyword : keywords) {
            if (tokenList.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
    
    private String getRandomResponse(String category) {
        List<String> categoryResponses = responses.getOrDefault(category, responses.get("default"));
        int index = random.nextInt(categoryResponses.size());
        return categoryResponses.get(index);
    }
    
    private Map<String, List<String>> initializeResponses() {
        Map<String, List<String>> responseMap = new HashMap<>();
        
        // Greeting responses
        responseMap.put("greeting", Arrays.asList(
            "Hello! How can I help you today?",
            "Hi there! What can I do for you?",
            "Greetings! How may I assist you?",
            "Hey! What's on your mind today?"
        ));
        
        // Identity responses
        responseMap.put("identity", Arrays.asList(
            "I'm a simple chatbot built with Spring Boot and OpenNLP.",
            "I'm your friendly neighborhood chatbot!",
            "I'm a virtual assistant designed to help answer your questions.",
            "You can call me ChatBot. I'm here to assist you."
        ));
        
        // Gratitude responses
        responseMap.put("gratitude", Arrays.asList(
            "You're welcome!",
            "Happy to help!",
            "Anytime!",
            "No problem at all!"
        ));
        
        // Farewell responses
        responseMap.put("farewell", Arrays.asList(
            "Goodbye! Have a great day!",
            "See you later!",
            "Bye for now! Come back soon!",
            "Take care!"
        ));
        
        // Help responses
        responseMap.put("help", Arrays.asList(
            "I can answer simple questions, provide information, or just chat. What do you need help with?",
            "I'm here to assist you. What would you like to know?",
            "How can I help you today? Feel free to ask me anything.",
            "I'm at your service. What kind of assistance do you need?"
        ));
        
        // Weather responses
        responseMap.put("weather", Arrays.asList(
            "I don't have access to real-time weather data, but I hope it's nice where you are!",
            "I can't check the weather for you, but maybe look outside?",
            "Weather forecasting isn't one of my capabilities yet.",
            "I wish I could tell you about the weather, but I don't have that functionality."
        ));
        
        // Time responses
        responseMap.put("time", Arrays.asList(
            "I don't have access to the current time or date.",
            "Time and date information isn't available to me.",
            "I can't tell you the exact time, but I'm always here when you need me!",
            "I don't have a clock, but it's always a good time to chat!"
        ));
        
        // How are you responses
        responseMap.put("how_are_you", Arrays.asList(
            "I'm doing well, thank you for asking! How about you?",
            "I'm functioning perfectly! How are you today?",
            "All systems operational! How's your day going?",
            "I'm great! Thanks for your concern. How can I help you?"
        ));
        
        // Empty input responses
        responseMap.put("empty", Arrays.asList(
            "I didn't catch that. Could you please say something?",
            "Hmm, it seems you didn't type anything. How can I help you?",
            "I'm listening, but I didn't hear anything. What's on your mind?",
            "Did you want to ask me something?"
        ));
        
        // Default responses
        responseMap.put("default", Arrays.asList(
            "I'm not sure I understand. Could you rephrase that?",
            "That's interesting, but I'm not sure how to respond.",
            "I'm still learning and don't have an answer for that yet.",
            "I don't have enough information to provide a good response to that.",
            "Could you try asking that in a different way?"
        ));
        
        return responseMap;
    }
}