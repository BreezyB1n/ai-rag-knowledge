package com.b1n.dev.tech.api;

import com.b1n.dev.tech.api.response.Response;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * RAG服务接口
 * @author zhangbin
 */
public interface IRAGService {
    /**
     * 查询RAG标签列表
     * @return 标签列表
     */
    Response<List<String>> queryRagTagList();

    /**
     * 上传文件到知识库
     * @param ragTag 知识库标签
     * @param files 要上传的文件列表
     * @return 上传结果
     */
    Response<String> upload(String ragTag, List<MultipartFile> files);

    Response<String> analyzeGitRepository(String repoUrl, String userName, String token) throws Exception;
}
