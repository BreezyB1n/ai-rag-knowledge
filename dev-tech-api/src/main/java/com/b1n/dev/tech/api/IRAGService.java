package com.b1n.dev.tech.api;

import com.b1n.dev.tech.api.response.Response;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @author zhangbin
 */
public interface IRAGService {

    Response<List<String>> queryRagTagList();

    Response<String> upload(String ragTag, List<MultipartFile> files);
}
