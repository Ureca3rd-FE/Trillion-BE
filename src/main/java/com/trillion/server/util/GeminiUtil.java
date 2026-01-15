package com.trillion.server.util;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;

@Component
public class GeminiUtil {
    private final Client client;

    public GeminiUtil(@Value("${GOOGLE_API_KEY}") String key) {

        // .env에서 API키 가져오기
        String apiKey = Objects.requireNonNull(
                key, "GOOGLE_API_KEY not set");

        this.client = Client.builder()
                .apiKey(apiKey)
                .build();
    }




    // gemini로 부터 프롬프트에 대한 결과 받아오기.
    public String generateResult( String prompt) {//model: gemini 모델
        try{
            GenerateContentConfig config = GenerateContentConfig.builder().
                    temperature((float) 0.9).
                    build();
            GenerateContentResponse res =
                    client.models.generateContent("gemini-2.5-flash", prompt, config);
            return res.text();
        }catch(Exception e){
            e.printStackTrace();
            return "AI 값을 받아오지 못함.";
        }
    }
}
