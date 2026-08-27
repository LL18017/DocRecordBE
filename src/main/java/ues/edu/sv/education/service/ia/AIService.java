package ues.edu.sv.education.service.ia;

import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import ues.edu.sv.education.service.UserType.UserTypeService;
import ues.edu.sv.education.service.auth.UserAuthService;
import ues.edu.sv.education.service.roles.RolesService;
import ues.edu.sv.education.service.user.UserService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class AIService {

    @Value("classpath:prompts/clinic-system-prompt.txt")
    Resource systemPrompt;
    private final ChatClient chatClient;
    private final RolesService rolesService;
    private final UserAuthService userAuthService;
    private final UserTypeService userTypeService;

    public AIService(ChatClient.Builder builder, ChatMemory chatMemory,RolesService rolesService,
                     UserAuthService userAuthService,UserTypeService userTypeService) {

        this.rolesService=rolesService;
        this.userAuthService=userAuthService;
        this.userTypeService=userTypeService;

        MessageChatMemoryAdvisor memoryAdvisor =
                MessageChatMemoryAdvisor.builder(chatMemory)
                        .build();

        this.chatClient = builder
                .defaultAdvisors(memoryAdvisor)
                .defaultTools(
                        userAuthService, rolesService,userTypeService
                )
                .build();
    }

    public String chat(String prompt, String userId) throws IOException {

        String system = systemPrompt.getContentAsString(StandardCharsets.UTF_8);

        Prompt aiPrompt = new Prompt(
                       prompt,
                OllamaChatOptions.builder()

                        .model("qwen3.5:4b")
                        .disableThinking()
                        .build()
        );

        return chatClient
                .prompt(aiPrompt)
                .advisors(advisorSpec ->
                        advisorSpec.param(
                                ChatMemory.CONVERSATION_ID,
                                userId
                        ))
                .system(system)
                .call()
                .content();
    }
}
