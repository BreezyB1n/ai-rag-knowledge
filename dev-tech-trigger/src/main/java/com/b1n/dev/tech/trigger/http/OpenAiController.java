package com.b1n.dev.tech.trigger.http;

import com.b1n.dev.tech.api.IAiService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author zhangbin
 */
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/openai/")
public class OpenAiController implements IAiService {
    private static final Logger log = LoggerFactory.getLogger(OpenAiController.class);
    @Resource
    private OpenAiChatClient chatClient;
    @Resource
    private PgVectorStore pgVectorStore;

    @RequestMapping(value = "generate", method = RequestMethod.GET)
    @Override
    public ChatResponse generate(@RequestParam("model") String model, @RequestParam("message") String message) {
        return chatClient.call(new Prompt(
                message,
                OpenAiChatOptions.builder()
                        .withModel(model)
                        .build()
        ));
    }

    /**
     * curl http://localhost:8090/api/v1/openai/generate_stream?model=gpt-4o&message=1+1
     */
    @RequestMapping(value = "generate_stream", method = RequestMethod.GET)
    @Override
    public Flux<ChatResponse> generateStream(@RequestParam("model") String model, @RequestParam("message") String message) {
        log.info("OpenAI用户提问: {}, 模型: {}", message, model);
        return chatClient.stream(new Prompt(
                message,
                OpenAiChatOptions.builder()
                        .withModel(model)
                        .build()
        ));
    }

    @RequestMapping(value = "generate_stream_rag", method = RequestMethod.GET)
    @Override
    public Flux<ChatResponse> generateStreamRag(@RequestParam("model") String model, @RequestParam("ragTag") String ragTag, @RequestParam("message") String message) {
        log.info("OpenAI+RAG用户提问: {}, 模型: {}, 知识库: {}", message, model, ragTag);

        String SYSTEM_PROMPT = """
                请仔细阅读下面的参考文档，并根据其内容回答用户的问题。
                如果参考文档中包含相关信息，请详细解答。
                如果参考文档中没有相关信息，请直接回答"根据参考文档，我无法回答这个问题"。
                请确保用中文回答。
                
                参考文档：
                {documents}
                """;

        // 指定文档搜索
        SearchRequest request = SearchRequest.query(message)
                .withTopK(5)
                .withFilterExpression("knowledge == '" + ragTag + "'");

        List<Document> documents = pgVectorStore.similaritySearch(request);
        log.info("OpenAI+RAG检索到文档数量: {}", documents.size());
        
        String documentCollectors = documents.stream().map(Document::getContent).collect(Collectors.joining("\n\n"));
        log.info("OpenAI+RAG文档内容长度: {}", documentCollectors.length());
        
        // 输出前300个字符用于调试
        if (!documents.isEmpty()) {
            log.info("OpenAI+RAG文档内容前300字符: {}", documentCollectors.substring(0, Math.min(documentCollectors.length(), 300)));
        }
        
        Message ragMessage = new SystemPromptTemplate(SYSTEM_PROMPT).createMessage(Map.of("documents", documentCollectors));

        List<Message> messages = new ArrayList<>();
        messages.add(ragMessage);
        messages.add(new UserMessage(message));


        return chatClient.stream(new Prompt(
                messages,
                OpenAiChatOptions.builder()
                        .withModel(model)
                        .build()
        ));
    }

}
