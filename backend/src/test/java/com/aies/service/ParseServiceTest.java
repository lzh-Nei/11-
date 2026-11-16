package com.aies.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ParseService 单测（UC-16 基本流第5步第一级解析：docx=POI / pdf=PDFBox，
 * 用例文件均由库自身在内存构造真实文件——不依赖测试资源）。
 */
class ParseServiceTest {

    private final ParseService parseService = new ParseService();

    /** 内存构造真实 docx（若干段落），返回字节流 */
    private byte[] docxBytes(String... paragraphs) throws IOException {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String text : paragraphs) {
                doc.createParagraph().createRun().setText(text);
            }
            doc.write(out);
            return out.toByteArray();
        }
    }

    /** 内存构造真实 pdf（标准14字体单行文本），返回字节流 */
    private byte[] pdfBytes(String text) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText(text);
                content.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** 例1 docx 多段落全文提取（UC-16 正文提取主路径） */
    @Test
    void extractDocxJoinsParagraphsAndTrims() throws IOException {
        byte[] bytes = docxBytes("软件工程实训", "任务书正文第二段");

        String text = parseService.extractDocx(new ByteArrayInputStream(bytes));

        assertThat(text).contains("软件工程实训").contains("任务书正文第二段");
        assertThat(text).isEqualTo(text.trim());
    }

    /** 例2 空文档不是解析失败——提取为空串（口径：仅损坏文件才 FAILED） */
    @Test
    void extractDocxEmptyDocumentYieldsBlank() throws IOException {
        byte[] bytes = docxBytes();

        assertThat(parseService.extractDocx(new ByteArrayInputStream(bytes))).isEmpty();
    }

    /** 例3 pdf 文本提取（PDFBox 3.x Loader 路径） */
    @Test
    void extractPdfReadsTextAndTrims() throws IOException {
        byte[] bytes = pdfBytes("AI Evaluation System");

        String text = parseService.extractPdf(new ByteArrayInputStream(bytes));

        assertThat(text).contains("AI Evaluation System");
        assertThat(text).isEqualTo(text.trim());
    }
}
