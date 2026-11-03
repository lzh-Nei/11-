package com.group11.aievaluate.util;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * MD5 工具：用于分片完整性校验（边拷贝边计算，全程流式，不占大内存）。
 */
public final class Md5Util {

    private Md5Util() {
    }

    public static MessageDigest md5() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 算法不可用", e);
        }
    }

    /** 计算流的 MD5（流会被读完但不关闭，由调用方管理） */
    public static String digest(InputStream in) throws IOException {
        MessageDigest md = md5();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            md.update(buf, 0, n);
        }
        return HexFormat.of().formatHex(md.digest());
    }

    /** 计算字符串的 MD5（用于生成上传会话 key） */
    public static String digest(String s) {
        return HexFormat.of().formatHex(md5().digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
