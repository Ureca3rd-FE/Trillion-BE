package com.trillion.server;

//import com.trillion.server.util.GeminiUtil;
import com.trillion.server.util.GeminiUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TrillionApplication {

	public static void main(String[] args) {
		SpringApplication.run(TrillionApplication.class, args);

        /* Gemini 사용 예시 코드
        * GeminiUtil gemini = new GeminiUtil(System.getenv("GOOGLE_API_KEY"));
        * System.out.println(gemini.generateResult("What's your name?"));
        */

	}

}
