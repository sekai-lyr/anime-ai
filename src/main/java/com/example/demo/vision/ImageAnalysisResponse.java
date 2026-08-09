package com.example.demo.vision;

import lombok.Data;

import java.util.List;

@Data
/**
图片分析响应模型。
 * 封装视觉分析结果：标题、描述、场景、情感、识别物体、标签和提取的文字。
 */
public class ImageAnalysisResponse {
    private String title;
    private String description;
    private List<String> objects;
    private String scene;
    private String emotion;
    private List<String> tags;
    private String text;
}