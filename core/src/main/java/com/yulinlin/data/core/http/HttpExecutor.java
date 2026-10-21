package com.yulinlin.data.core.http;

import java.nio.file.Path;

/**
 * 可替换的同步 HTTP 执行协议。实现必须线程安全并复用连接资源。
 * 请求中的 URL 已完成编码，超时和大小限制已合并默认配置，不得再次编码 URL 或忽略限制。
 * 网络异常统一抛出 HttpRequestException；不默认重试或跟随重定向。
 */
public interface HttpExecutor extends AutoCloseable {

    /** 正文受 maxResponseSize 限制；4xx/5xx 抛出异常，错误正文按 maxErrorSize 截断。 */
    HttpResponse execute(HttpRequestData request);

    /** 只返回状态码，包括 4xx/5xx，不读取完整正文；网络错误仍抛出异常。 */
    int status(HttpRequestData request);

    /** 流式下载完整文件，成功后替换目标，失败清理临时文件；返回响应正文为空。 */
    HttpResponse download(HttpRequestData request, Path destination);

    /** 只关闭自己创建的资源；外部共享客户端由其所有者负责。 */
    @Override
    default void close() { }
}
