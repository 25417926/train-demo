package com.guo.member.service;

import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
@Service
public class ChatService {
    private static final String ARK_API_KEY = "12018dc6-4159-4316-8177-88a7649b56d8";
    private static final ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.SECONDS);
    private static final Dispatcher dispatcher = new Dispatcher();
    private static final ArkService service = ArkService.builder()
            .dispatcher(dispatcher)
            .connectionPool(connectionPool)
            .baseUrl("https://ark.cn-beijing.volces.com/api/v3")
            .apiKey(ARK_API_KEY)
            .build();

    /**
     * 发起标准聊天完成请求
     * @param userContent 用户输入的内容
     * @return 聊天完成的结果列表
     */
    public List<String> createStandardChatCompletion(String userContent) {
        List<ChatMessage> messages = buildMessages(userContent);
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model("ep-20250125182123-tqjg6")
                .messages(messages)
                .build();

        return service.createChatCompletion(chatCompletionRequest)
                .getChoices()
                .stream()
                .map(choice ->  (String) choice.getMessage().getContent())
                .collect(Collectors.toList());
    }

    /**
     * 发起流式聊天完成请求
     * @param userContent 用户输入的内容
     * @return 聊天完成的结果字符串
     */
    public String createStreamingChatCompletion(String userContent) {
        List<ChatMessage> streamMessages = buildMessages(userContent);
        ChatCompletionRequest streamChatCompletionRequest = ChatCompletionRequest.builder()
                .model("ep-20250125182123-tqjg6")
                .messages(streamMessages)
                .build();

        StringBuilder result = new StringBuilder();
        service.streamChatCompletion(streamChatCompletionRequest)
                .doOnError(Throwable::printStackTrace)
                .blockingForEach(
                        choice -> {
                            if (choice.getChoices().size() > 0) {
                                result.append(choice.getChoices().get(0).getMessage().getContent());
                            }
                        }
                );

        return result.toString();
    }

    private List<ChatMessage> buildMessages(String userContent) {
        List<ChatMessage> messages = new ArrayList<>();
        ChatMessage systemMessage = ChatMessage.builder()
                .role(ChatMessageRole.SYSTEM)
                .content("你是豆包，是由字节跳动开发的 AI 人工智能助手")
                .build();
        ChatMessage userMessage = ChatMessage.builder()
                .role(ChatMessageRole.USER)
                .content(userContent)
                .build();
        messages.add(systemMessage);
        messages.add(userMessage);
        return messages;
    }
}
