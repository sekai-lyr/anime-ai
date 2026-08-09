package com.example.demo.agent.tools;

import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
工具抽象基类。
 * 所有Agent工具的父类，定义了工具的标准接口：getName、getDescription、getDefinition、execute。
 * 提供safeExecute模板方法统一处理异常。
 */
public abstract class BaseTool {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    public abstract String getName();

    public abstract String getDescription();

    public abstract ToolDefinition getDefinition();

    public abstract ToolResult<?> execute(JSONObject params);

    public String getSchemaJson() {
        return getDefinition().toJson();
    }

    protected ToolResult<?> safeExecute(JSONObject params) {
        try {
            return execute(params);
        } catch (IllegalArgumentException e) {
            logger.warn("Tool {} received invalid arguments: {}", getName(), e.getMessage());
            return ToolResult.failure(e.getMessage());
        } catch (Exception e) {
            logger.error("Tool {} execution failed", getName(), e);
            return ToolResult.failure("工具执行失败，请重试。");
        }
    }
}