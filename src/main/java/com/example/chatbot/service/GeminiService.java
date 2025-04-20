package com.example.chatbot.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;
    
    private final Gson gson = new Gson();
    private final Random random = new Random();
    
    private final Map<String, List<String>> localResponses = createLocalResponses();
    private final List<QuestionPattern> questionPatterns = createQuestionPatterns();
    private final List<Joke> jokes = createJokes();

    private static final String SYSTEM_PROMPT = "You are an AI chatbot named ChatBot. Your goal is to be helpful, friendly, and conversational. Respond clearly and concisely.";

    /**
     * Process a user prompt and generate a response
     */
    public String generateResponse(String prompt) {
        System.out.println("\n\n=====================================");
        System.out.println("GEMINI SERVICE - Processing: " + prompt);
        
        String lowercasePrompt = prompt.toLowerCase();
        
        // 1. Try local intelligence
        String localResponse = getLocalResponse(lowercasePrompt);
        if (localResponse != null) {
            System.out.println("Using local intelligence: " + localResponse);
            return localResponse;
        }
        
        // 2. Try Gemini API (simplified call)
        // Use a primary model first, maybe fallback to another if needed
        String[] modelsToTry = {"gemini-2.0-flash", "gemini-1.5-flash-latest"};
        String apiResponse = null;
        for (String model : modelsToTry) {
             System.out.println("Attempting API call with model: " + model);
             apiResponse = callSimplifiedGeminiApi(prompt, model);
             if (apiResponse != null && !apiResponse.isEmpty()) {
                 System.out.println("API call successful with model: " + model);
                 return apiResponse;
             }
             System.out.println("API call failed or returned empty for model: " + model);
        }
        
        // 3. If all fail, use enhanced fallback
        System.out.println("API calls failed, using enhanced fallback.");
        return getEnhancedFallback(lowercasePrompt);
    }
    
    /**
     * Simplified call to the Gemini API - single attempt, primary endpoint/payload.
     * Includes a system prompt for chatbot persona.
     */
    private String callSimplifiedGeminiApi(String prompt, String model) {
        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("API key is not configured");
            return null;
        }

        // Construct the payload with system prompt and user prompt
        JsonObject userPromptPart = new JsonObject();
        userPromptPart.addProperty("text", prompt);

        JsonObject systemPromptPart = new JsonObject();
        systemPromptPart.addProperty("text", SYSTEM_PROMPT);

        // Create contents array
        JsonArray contentsArray = new JsonArray();
        
        JsonObject systemTurn = new JsonObject();
        systemTurn.addProperty("role", "user"); // System prompts often go in the first 'user' turn
        JsonArray systemParts = new JsonArray();
        systemParts.add(systemPromptPart);
        systemTurn.add("parts", systemParts);
        contentsArray.add(systemTurn);

        // Add the actual user prompt turn
        JsonObject userTurn = new JsonObject();
        userTurn.addProperty("role", "user");
        JsonArray userParts = new JsonArray();
        userParts.add(userPromptPart);
        userTurn.add("parts", userParts);
        contentsArray.add(userTurn);
        
        JsonObject payloadObject = new JsonObject();
        payloadObject.add("contents", contentsArray);

        // Add generation config if needed (optional)
        // JsonObject generationConfig = new JsonObject();
        // generationConfig.addProperty("temperature", 0.7);
        // generationConfig.addProperty("maxOutputTokens", 1024);
        // payloadObject.add("generationConfig", generationConfig);

        String payload = gson.toJson(payloadObject);
        
        // Use primary v1beta endpoint
        String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;
        
        HttpURLConnection conn = null;
        try {
            System.out.println("Trying URL: " + apiUrl.replace(apiKey, "API_KEY_HIDDEN"));
            System.out.println("Using payload: " + payload); // Log the full payload
            
            URL url = new URL(apiUrl);
            conn = (HttpURLConnection) url.openConnection(); 
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000); // 10 seconds
            conn.setReadTimeout(15000); // 15 seconds
            
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = payload.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            System.out.println("Response Code: " + responseCode);
            
            if (responseCode == 200) {
                // Successful response
                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                }
                String responseBody = response.toString();
                System.out.println("Raw API response: " + responseBody);
                return parseApiResponse(responseBody); // Use existing parser
            } else {
                // Log error for non-200 responses
                System.out.println("API call failed with code: " + responseCode);
                logErrorStream(conn); 
                return null; // Indicate failure
            } 
        } catch (IOException e) {
            // Log connection errors
            System.out.println("Connection error with URL " + apiUrl + ": " + e.getMessage());
            return null; // Indicate failure
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Helper method to parse potential API response formats
     */
    private String parseApiResponse(String responseBody) {
        try {
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            
            // Format 1: candidates -> content -> parts -> text
            if (jsonResponse.has("candidates")) {
                try {
                    return jsonResponse
                        .getAsJsonArray("candidates")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("content")
                        .getAsJsonArray("parts")
                        .get(0).getAsJsonObject()
                        .get("text").getAsString();
                } catch (Exception e) {
                    System.out.println("Failed to parse candidates format: " + e.getMessage());
                }
            }
            
            // Format 2: text directly (less common now)
            if (jsonResponse.has("text")) {
                 try {
                    return jsonResponse.get("text").getAsString();
                 } catch (Exception e) {
                    System.out.println("Failed to parse direct text format: " + e.getMessage());
                 }
            }
            
            // Format 3: response -> text (hypothetical)
            if (jsonResponse.has("response") && jsonResponse.getAsJsonObject("response").has("text")) {
                 try {
                    return jsonResponse.getAsJsonObject("response").get("text").getAsString();
                 } catch (Exception e) {
                    System.out.println("Failed to parse response->text format: " + e.getMessage());
                 }
            }
            
            // If we got a valid JSON response but can't parse it in known formats, return a summary
            return "Received a JSON response, but couldn't extract text in expected formats.";
        } catch (Exception e) {
            System.out.println("Error parsing JSON response: " + e.getMessage());
            return "Received a non-JSON or invalid response from the API.";
        }
    }

    /**
     * Helper method to log error stream from HttpURLConnection
     */
    private void logErrorStream(HttpURLConnection conn) {
        if (conn == null) return;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getErrorStream(), "utf-8"))) {
            StringBuilder errorResponse = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                errorResponse.append(responseLine.trim());
            }
            System.out.println("Error body: " + errorResponse.toString());
        } catch (IOException | NullPointerException e) { // Catch NPE if getErrorStream is null
            System.out.println("Error reading error stream: " + e.getMessage());
        }
    }
    
    /**
     * Check if we can answer this locally without API
     */
    private String getLocalResponse(String lowercasePrompt) {
        // Time and date patterns
        if (lowercasePrompt.contains("time")) {
            if (lowercasePrompt.contains("current") || lowercasePrompt.contains("now") || 
                lowercasePrompt.contains("what") || lowercasePrompt.matches(".*what.*time.*")) {
                LocalTime now = LocalTime.now();
                return "The current time is " + now.format(DateTimeFormatter.ofPattern("h:mm a")) + ".";
            }
        }
        
        if ((lowercasePrompt.contains("today") || lowercasePrompt.contains("current")) && 
            (lowercasePrompt.contains("date") || lowercasePrompt.contains("day"))) {
            LocalDate today = LocalDate.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy");
            return "Today's date is " + today.format(formatter) + ".";
        }

        // Check for telling a joke
        if (lowercasePrompt.contains("tell me a joke") || lowercasePrompt.contains("joke")) {
            return getRandomJoke();
        }
        
        // Check for question patterns
        for (QuestionPattern pattern : questionPatterns) {
            Matcher matcher = pattern.pattern.matcher(lowercasePrompt);
            if (matcher.find()) {
                List<String> responses = pattern.responses;
                // Handle specific captured groups if needed (e.g., for capital city)
                if (pattern.pattern.pattern().contains("capital of (\\w+)")) {
                    String country = matcher.group(1);
                    return getCapitalCityResponse(country);
                }
                return responses.get(random.nextInt(responses.size()));
            }
        }
        
        // Simple keyword matching for common questions/phrases
        for (Map.Entry<String, List<String>> entry : localResponses.entrySet()) {
            String keyword = entry.getKey();
            if (lowercasePrompt.contains(keyword)) {
                List<String> responses = entry.getValue();
                return responses.get(random.nextInt(responses.size()));
            }
        }
        
        // Math calculation patterns
        Pattern mathPattern = Pattern.compile("(\\d+)\\s*([+\\-*/])\\s*(\\d+)");
        Matcher mathMatcher = mathPattern.matcher(lowercasePrompt);
        if (mathMatcher.find()) {
            try {
                int num1 = Integer.parseInt(mathMatcher.group(1));
                String op = mathMatcher.group(2);
                int num2 = Integer.parseInt(mathMatcher.group(3));
                double result = 0;
                
                switch (op) {
                    case "+": result = num1 + num2; break;
                    case "-": result = num1 - num2; break;
                    case "*": result = num1 * num2; break;
                    case "/": 
                        if (num2 == 0) return "I cannot divide by zero.";
                        result = (double)num1 / num2; 
                        break;
                }
                
                if (op.equals("/") && result != (int)result) {
                    return String.format("%d %s %d = %.2f", num1, op, num2, result);
                } else {
                    return String.format("%d %s %d = %d", num1, op, num2, (int)result);
                }
            } catch (Exception e) {
                // If parsing fails, continue with other methods
            }
        }
        
        return null; // No local response found
    }

    // Method to handle capital city specifically (example of pattern group usage)
    private String getCapitalCityResponse(String country) {
        Map<String, String> capitals = new HashMap<>();
        capitals.put("france", "Paris");
        capitals.put("germany", "Berlin");
        capitals.put("japan", "Tokyo");
        capitals.put("usa", "Washington D.C.");
        capitals.put("canada", "Ottawa");
        // Add more capitals

        String lowerCaseCountry = country.toLowerCase();
        if (capitals.containsKey(lowerCaseCountry)) {
            return "The capital of " + country + " is " + capitals.get(lowerCaseCountry) + ".";
        } else {
            return "I know some capitals, but I don't have the capital of " + country + " in my current knowledge base.";
        }
    }
    
    /**
     * Create enhanced fallback responses when API fails
     */
    private String getEnhancedFallback(String lowercasePrompt) {
        // Check for specific topics we have some knowledge about
        if (lowercasePrompt.contains("java") || lowercasePrompt.contains("programming")) {
            return "I have some basic knowledge about programming. Java is an object-oriented programming language used for building applications. It's known for its 'write once, run anywhere' capability through the Java Virtual Machine (JVM).";
        }
        
        if (lowercasePrompt.contains("spring") || lowercasePrompt.contains("spring boot")) {
            return "Spring Boot is a Java-based framework used for building web applications and microservices. It simplifies the setup and development of new Spring applications through auto-configuration and opinionated defaults.";
        }
        
        if (lowercasePrompt.contains("chatbot") || lowercasePrompt.contains("how do you work")) {
            return "I'm a simple chatbot built with Spring Boot and Java. I use pattern matching for simple queries and can handle date/time questions, basic math, and some informational questions. However, I don't currently have access to my full knowledge base, so my capabilities are limited.";
        }
        
        // Greeting patterns
        if (lowercasePrompt.contains("hello") || lowercasePrompt.contains("hi") || 
            lowercasePrompt.startsWith("hey") || lowercasePrompt.contains("greetings")) {
            return "Hello! I'm having trouble connecting to my knowledge source, but I can still help with basic questions about time, date, simple math, or some programming topics.";
        }
        
        // Question patterns
        if (lowercasePrompt.contains("who") || lowercasePrompt.contains("what") || 
            lowercasePrompt.contains("when") || lowercasePrompt.contains("where") || 
            lowercasePrompt.contains("why") || lowercasePrompt.contains("how")) {
            return "I'd like to help with your question, but I'm currently having trouble accessing my full knowledge base. I can still help with basic information like time, date, simple math, or some programming topics. Could you try a more specific question in one of those areas?";
        }
        
        // General fallback
        return "I apologize, but I'm having difficulty connecting to my knowledge source right now. I can still help with basic information like current time, date, simple calculations, or some programming topics.";
    }
    
    /**
     * Initialize local response patterns
     */
    private Map<String, List<String>> createLocalResponses() {
        Map<String, List<String>> responses = new HashMap<>();
        
        // Greetings
        responses.put("hello", new ArrayList<>(List.of("Hello there!", "Hi!", "Greetings!")));
        responses.put("hi", new ArrayList<>(List.of("Hello!", "Hey!", "Hi there!")));
        responses.put("hey", new ArrayList<>(List.of("Hey!", "Hello!", "What's up?")));
        responses.put("good morning", new ArrayList<>(List.of("Good morning! I hope you have a great day.")));
        responses.put("good afternoon", new ArrayList<>(List.of("Good afternoon!")));
        responses.put("good evening", new ArrayList<>(List.of("Good evening!")));

        // Basic Info / Capabilities
        responses.put("name", new ArrayList<>(List.of(
            "I am a simple chatbot built with Spring Boot.",
            "You can call me ChatBot. I'm here to assist you.",
            "My name is ChatBot."
        )));
        responses.put("what can you do", new ArrayList<>(List.of(
            "I can answer basic questions, tell the time and date, do simple math, and tell jokes. My connection to more advanced knowledge is currently limited.",
            "Currently, I can handle simple tasks like telling time, date, basic math, and sharing a joke or two.",
            "I have some built-in capabilities for common questions, time, date, and calculations."
        )));
        responses.put("who made you", new ArrayList<>(List.of(
            "I was developed as a project using Spring Boot and Java.",
            "I'm the result of a coding project."
        )));
        
        // Weather (Still limited)
        responses.put("weather", new ArrayList<>(List.of(
            "I'm sorry, I don't have access to real-time weather data.",
            "I can't check the weather for you, but I hope it's nice where you are!",
            "My apologies, checking the weather is beyond my current capabilities."
        )));
        
        // Politeness / Social
        responses.put("how are you", new ArrayList<>(List.of(
            "I'm functioning as expected, thank you for asking!",
            "I'm doing great! Ready to help. How can I assist you?",
            "All systems nominal! Thanks for asking."
        )));
        responses.put("thank", new ArrayList<>(List.of(
            "You're welcome!",
            "Happy to help!",
            "Anytime!",
            "No problem!"
        )));
        responses.put("sorry", new ArrayList<>(List.of(
            "No worries.",
            "It's okay.",
            "That's alright."
        )));
        
        // Farewells
        responses.put("bye", new ArrayList<>(List.of(
            "Goodbye! Have a great day!",
            "See you later!",
            "Take care!",
            "Farewell!"
        )));
        responses.put("see you", new ArrayList<>(List.of("See you later!", "Goodbye!")));
        
        return responses;
    }
    
    /**
     * Create regex pattern matchers for common questions
     */
    private List<QuestionPattern> createQuestionPatterns() {
        List<QuestionPattern> patterns = new ArrayList<>();
        
        // Basic information about Java
        patterns.add(new QuestionPattern(
            Pattern.compile("what is java|tell me about java|explain java"),
            new ArrayList<>(List.of(
                "Java is a class-based, object-oriented programming language known for 'write once, run anywhere'.",
                "Java is a popular, high-level language used for web apps, Android apps, and enterprise software."
            ))
        ));
        
        // Information about Spring Boot
        patterns.add(new QuestionPattern(
            Pattern.compile("what is spring boot|tell me about spring boot|explain spring boot"),
            new ArrayList<>(List.of(
                "Spring Boot is a Java framework that simplifies creating stand-alone, production-grade Spring applications.",
                "Spring Boot makes it easy to build web applications and microservices in Java with less configuration."
            ))
        ));
        
        // Information about chatbots
        patterns.add(new QuestionPattern(
            Pattern.compile("what is a chatbot|how do chatbots work|explain chatbots"),
            new ArrayList<>(List.of(
                "A chatbot simulates human conversation using rules or AI to understand and respond to users.",
                "Chatbots are programs designed to interact with humans via text or voice, often for customer service or information retrieval."
            ))
        ));

        // Definition example
        patterns.add(new QuestionPattern(
            Pattern.compile("what is (?:the )?meaning of (\\w+)|define (\\w+)"), // Captures word to define
            new ArrayList<>(List.of(
                 // Response handled dynamically later or generic fallback
                 "I can provide definitions for some common words, but my dictionary is limited right now."
            ))
            // Note: Actual definition logic would need a local dictionary or API call.
        ));

        // Capital city example (specific handling in getLocalResponse)
        patterns.add(new QuestionPattern(
            Pattern.compile("what is the capital of (\\w+)|capital of (\\w+)"), // Captures country
            new ArrayList<>(List.of(
                // Response handled dynamically in getLocalResponse using the captured group.
                "Let me check my records for that capital."
            ))
        ));

        // Simple conversion example (can be expanded)
        patterns.add(new QuestionPattern(
            Pattern.compile("how many (\\w+) are in a (\\w+)"), // e.g., how many cm are in a meter
            new ArrayList<>(List.of(
                "I can handle some basic conversions, but complex ones might be tricky."
                // Add logic later if desired
            ))
        ));
        
        return patterns;
    }
    
    /**
     * Inner class to hold pattern and responses
     */
    private static class QuestionPattern {
        Pattern pattern;
        List<String> responses;
        
        QuestionPattern(Pattern pattern, List<String> responses) {
            this.pattern = pattern;
            this.responses = responses;
        }
    }
    
    /**
     * NEW: Inner class for Jokes
     */
    private static class Joke {
        String setup;
        String punchline;

        Joke(String setup, String punchline) {
            this.setup = setup;
            this.punchline = punchline;
        }
    }

    /**
     * NEW: Create a list of jokes
     */
    private List<Joke> createJokes() {
        List<Joke> jokeList = new ArrayList<>();
        jokeList.add(new Joke("Why don't scientists trust atoms?", "Because they make up everything!"));
        jokeList.add(new Joke("Why did the scarecrow win an award?", "Because he was outstanding in his field!"));
        jokeList.add(new Joke("What do you call fake spaghetti?", "An impasta!"));
        jokeList.add(new Joke("Why did the bicycle fall over?", "Because it was two tired!"));
        jokeList.add(new Joke("What did the left eye say to the right eye?", "Between you and me, something smells!"));
        // Add more jokes
        return jokeList;
    }

    /**
     * NEW: Get a random joke
     */
    private String getRandomJoke() {
        if (jokes.isEmpty()) {
            return "I'm out of jokes at the moment!";
        }
        Joke joke = jokes.get(random.nextInt(jokes.size()));
        return joke.setup + "\n" + joke.punchline;
    }
    
    /**
     * Simple test method
     */
    public String testGeminiApi() {
        String testPrompt = "Hello, can you respond with 'yes' if you can see this message?";
        return generateResponse(testPrompt);
    }
} 