package ues.edu.sv.education.controller.ia;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ues.edu.sv.education.model.dto.auth.CustomUserDetails;
import ues.edu.sv.education.model.dto.ia.ChatRequest;
import ues.edu.sv.education.service.ia.AIService;

import java.io.IOException;

@RestController
@RequestMapping("/ai")
public class AIController {
    private final AIService aiService;

    public AIController(AIService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/chat")
    public ResponseEntity<String> chat(
            @RequestBody ChatRequest request) throws IOException {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        String username = authentication.getName();

        String response =
                aiService.chat(request.prompt(), username);

        return ResponseEntity.ok(response);
    }
}
