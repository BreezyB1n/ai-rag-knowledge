package com.b1n.dev.tech.trigger.http;

import com.b1n.dev.tech.api.IAiService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author zhangbin
 */
@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/ollama")
public class OllamaController implements IAiService {

    @Resource
    private OllamaChatClient chatClient;

    @Resource
    private PgVectorStore pgVectorStore;

    /**
     * 非流式回答
     * @param model
     * @param message
     * @return
     */
    @RequestMapping(value = "generate", method = RequestMethod.GET)
    @Override
    public ChatResponse generate(@RequestParam(name = "model") String model, @RequestParam(name = "message") String message) {
        return chatClient.call(new Prompt(message, OllamaOptions.create().withModel(model)));
    }

    /**
     * http://localhost:8090/api/v1/ollama/generate_stream?model=deepseek-r1:1.5b&message=hi
     * @param model
     * @param message
     * @return
     */
    @RequestMapping(value = "generate_stream", method = RequestMethod.GET)
    @Override
    public Flux<ChatResponse> generateStream(@RequestParam(name = "message") String message, @RequestParam(name = "model") String model) {
        log.info("Ollama用户提问: {}, 模型: {}", message, model);
        return chatClient.stream(new Prompt(message, OllamaOptions.create().withModel(model)));
    }

    @RequestMapping(value = "generate_stream_rag", method = RequestMethod.GET)
    @Override
    public Flux<ChatResponse> generateStreamRag(@RequestParam("model") String model, @RequestParam("ragTag") String ragTag, @RequestParam("message") String message) {
        log.info("Ollama+RAG用户提问: {}, 模型: {}, 知识库: {}", message, model, ragTag);

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
        log.info("Ollama+RAG搜索请求: {}", request);

        List<Document> documents = pgVectorStore.similaritySearch(request);
        log.info("Ollama+RAG检索到文档数量: {}", documents.size());
        
        String documentCollectors = documents.stream().map(Document::getContent).collect(Collectors.joining("\n\n"));
        log.info("Ollama+RAG文档内容长度: {}", documentCollectors.length());
        
        // 输出前300个字符用于调试
        if (!documents.isEmpty()) {
            log.info("Ollama+RAG文档内容前300字符: {}", documentCollectors.substring(0, Math.min(documentCollectors.length(), 300)));
        }
        
        Message ragMessage = new SystemPromptTemplate(SYSTEM_PROMPT).createMessage(Map.of("documents", documentCollectors));

        List<Message> messages = new ArrayList<>();
        messages.add(ragMessage);
        messages.add(new UserMessage(message));

        log.info("Ollama+RAG准备发送请求，消息数: {}, 模型: {}", messages.size(), model);
        
        return chatClient.stream(new Prompt(
                messages,
                OllamaOptions.create()
                        .withModel(model)
                        .withTemperature(0.7f)
                        .withNumPredict(2048)
        ));
    }
}
