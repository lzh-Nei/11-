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
 * OCR/图片三级解析属后续扩展（compose ocr 占位容器），当前白名单仅 docx/pdf。</p>
 *
 * <p>口径：提取成功返回全文（可为空串——空文档不是解析失败，UC-16 E4
 * 仅指损坏/无法读取）；抛异常即解析失败（worker 置 FAILED）。</p>
 */
@Slf4j
@Service
public class ParseService {

    /** docx 全文提取（段落+表格，POI XWPFWordExtractor 已含表格文本） */
    public String extractDocx(InputStream in) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText().trim();
        }
    }

    /** pdf 全文提取（PDFBox 3.x：Loader + PDFTextStripper） */
    public String extractPdf(InputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(doc).trim();
        }
    }
}
