package com.aies.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文档文本提取（UC-16 基本流第5步"异步三级解析"的第一级：文本提取）。
 *
 * <p>docx → POI XWPF（黄金集生成时已验证能力）；pdf → PDFBox 3.x Loader。
 * compose 的 ocr 容器仅为 http.server 占位（无 OCR API），故图片/扫描件不进入解析链：
 * 上传层明确拒绝，解析层空文本置 FAILED，避免静默产出空内容。</p>
 *
 * <p>口径：提取成功返回非空全文；空文本/损坏/无法读取一律抛异常（worker 置 FAILED）。</p>
 */
@Slf4j
@Service
public class ParseService {

    /** docx 全文提取（段落+表格，POI XWPFWordExtractor 已含表格文本） */
    public String extractDocx(InputStream in) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return requireText(extractor.getText());
        }
    }

    /** pdf 全文提取（PDFBox 3.x：Loader + PDFTextStripper） */
    public String extractPdf(InputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            return requireText(new PDFTextStripper().getText(doc));
        }
    }

    /**
     * 空文本视为解析失败：扫描件/纯图片 PDF 没有可提取文本，若按 SUCCESS 落库会静默产出空内容，
     * 下游评价无意义。此处显式抛异常，由 ParseWorker 置 FAILED 并给出可读原因。
     */
    private String requireText(String text) throws IOException {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            throw new IOException("未提取到可解析文本（可能为扫描件/图片），"
                    + "请上传可复制文本的 PDF 或 docx");
        }
        return trimmed;
    }
}
