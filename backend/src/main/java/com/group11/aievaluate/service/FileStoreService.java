package com.group11.aievaluate.service;

import com.group11.aievaluate.config.FileStoreProperties;
import com.group11.aievaluate.model.BatchItemResult;
import com.group11.aievaluate.model.ChunkMeta;
import com.group11.aievaluate.model.ChunkResult;
import com.group11.aievaluate.model.CompleteResponse;
import com.group11.aievaluate.model.FileMeta;
import com.group11.aievaluate.model.InitResponse;
import com.group11.aievaluate.model.UploadSession;
import com.group11.aievaluate.util.Md5Util;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 文件存储核心服务：分片上传 / 校验与合并 / 批量上传 / 文件登记。
 *
 * 目录布局：
 *   {baseDir}/chunks/{uploadId}/0,1,2...      分片数据
 *   {baseDir}/chunks/{uploadId}/0.meta.json   每个分片的 MD5 与大小（合并前校验用）
 *   {baseDir}/chunks/{uploadId}/upload.json   会话信息
 *   {baseDir}/files/{fileId}.bin              合并后的文件
 *   {baseDir}/files/{fileId}.meta.json        文件元数据
 */
@Service
public class FileStoreService {

    /** uploadId / fileId 均为 32 位十六进制，杜绝路径穿越 */
    private static final Pattern SAFE_ID = Pattern.compile("^[a-f0-9]{32}$");
    private static final Pattern SAFE_NAME = Pattern.compile("^[^/\\\\<>:\"|?*\u0000-\u001f]{1,200}$");

    private final Path chunkDir;
    private final Path fileDir;
    private final ObjectMapper om = new ObjectMapper();
    /** 合并是“读全部分片→写一个文件”，按 uploadId 加锁防止并发重复合并 */
    private final Map<String, Object> completeLocks = new ConcurrentHashMap<>();

    public FileStoreService(FileStoreProperties props) throws IOException {
        Path base = Paths.get(props.getBaseDir()).toAbsolutePath().normalize();
        this.chunkDir = base.resolve("chunks");
        this.fileDir = base.resolve("files");
        Files.createDirectories(chunkDir);
        Files.createDirectories(fileDir);
    }

    /* ==================== 1. 大文件分片上传 ==================== */

    /**
     * 初始化分片上传。
     * uploadId = md5(文件名|大小|最后修改时间|分片大小)：同一文件重复初始化会落到同一会话，
     * 从而扫描出“已上传分片”返回给客户端 —— 这就是上传断点续传的恢复依据。
     */
    public InitResponse initUpload(String fileName, long fileSize, long lastModified, long chunkSize, int totalChunks)
            throws IOException {
        if (fileName == null || !SAFE_NAME.matcher(fileName).matches()) {
            throw new IllegalArgumentException("非法文件名");
        }
        if (fileSize <= 0 || chunkSize <= 0 || totalChunks <= 0 || totalChunks > 100_000) {
            throw new IllegalArgumentException("文件大小 / 分片大小 / 分片数非法");
        }
        ensureDirs();
        String uploadId = Md5Util.digest(fileName + "|" + fileSize + "|" + lastModified + "|" + chunkSize);
        Path dir = chunkDir.resolve(uploadId);
        Files.createDirectories(dir);
        om.writeValue(dir.resolve("upload.json").toFile(),
                new UploadSession(fileName, fileSize, totalChunks, chunkSize));

        // 扫描已落盘且带校验元数据的分片序号
        List<Integer> uploaded = new ArrayList<>();
        try (Stream<Path> list = Files.list(dir)) {
            list.map(p -> p.getFileName().toString())
                    .filter(n -> n.chars().allMatch(Character::isDigit))
                    .map(Integer::parseInt)
                    .filter(i -> Files.isRegularFile(dir.resolve(i + ".meta.json")))
                    .sorted()
                    .forEach(uploaded::add);
        }
        return new InitResponse(uploadId, fileName, fileSize, totalChunks, uploaded);
    }

    /**
     * 接收一个分片：边收边算 MD5，先写临时文件再原子改名，并把 MD5/大小记录到 meta。
     */
    public ChunkResult saveChunk(String uploadId, int chunkIndex, MultipartFile file) throws IOException {
        Path dir = requireUploadDir(uploadId);
        UploadSession session = readSession(dir);
        if (chunkIndex < 0 || chunkIndex >= session.totalChunks()) {
            throw new IllegalArgumentException("分片序号越界: " + chunkIndex);
        }
        Path tmp = dir.resolve(chunkIndex + ".part");
        Path dest = dir.resolve(String.valueOf(chunkIndex));
        String md5;
        try (InputStream in = file.getInputStream();
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(tmp), 1 << 16)) {
            MessageDigest md = Md5Util.md5();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                md.update(buf, 0, n);
                out.write(buf, 0, n);
            }
            md5 = HexFormat.of().formatHex(md.digest());
        }
        // 原子落盘，避免服务重启/并发写导致“半包分片”混入
        Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
        om.writeValue(dir.resolve(chunkIndex + ".meta.json").toFile(), new ChunkMeta(md5, file.getSize()));
        return new ChunkResult(true, chunkIndex, md5, "分片接收成功");
    }

    /**
     * 请求合并。流程：
     *   第一步：逐个校验分片（存在 + 大小一致 + MD5 一致），发现坏块 → 返回 badChunks 通知客户端定向重传；
     *   第二步：按序合并为最终文件，同时计算整文件 MD5（可用于秒传/去重），登记元数据并清理分片目录。
     */
    public CompleteResponse complete(String uploadId) throws IOException {
        Path dir = requireUploadDir(uploadId);
        Object lock = completeLocks.computeIfAbsent(uploadId, k -> new Object());
        synchronized (lock) {
            try {
                UploadSession session = readSession(dir);

                // —— 第一步：全量校验 ——
                List<Integer> bad = new ArrayList<>();
                for (int i = 0; i < session.totalChunks(); i++) {
                    if (!chunkHealthy(dir, i)) {
                        bad.add(i);
                    }
                }
                if (!bad.isEmpty()) {
                    return CompleteResponse.fail(bad, "检测到 " + bad.size() + " 个损坏/缺失分片，请重传: " + bad);
                }

                // —— 第二步：按序合并 ——
                String fileId = UUID.randomUUID().toString().replace("-", "");
                Path target = fileDir.resolve(fileId + ".bin");
                MessageDigest totalMd5 = Md5Util.md5();
                long copied = 0;
                try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(target), 1 << 16)) {
                    for (int i = 0; i < session.totalChunks(); i++) {
                        long chunkCopied = 0;
                        try (InputStream in = new BufferedInputStream(
                                Files.newInputStream(dir.resolve(String.valueOf(i))), 1 << 16)) {
                            MessageDigest chunkMd = Md5Util.md5();
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = in.read(buf)) != -1) {
                                chunkMd.update(buf, 0, n);
                                totalMd5.update(buf, 0, n);
                                out.write(buf, 0, n);
                                chunkCopied += n;
                            }
                            // 合并瞬间再次核对（防止校验通过后、读取前分片又被篡改）
                            String actual = HexFormat.of().formatHex(chunkMd.digest());
                            if (!actual.equals(readChunkMeta(dir, i).md5())) {
                                throw new IllegalStateException("分片 " + i + " 在合并时校验失败");
                            }
                        }
                        copied += chunkCopied;
                    }
                }

                FileMeta meta = new FileMeta(fileId, session.fileName(), copied,
                        HexFormat.of().formatHex(totalMd5.digest()),
                        LocalDateTime.now().withNano(0).toString());
                om.writeValue(fileDir.resolve(fileId + ".meta.json").toFile(), meta);

                deleteRecursively(dir);   // 合并成功后清理分片
                completeLocks.remove(uploadId);
                return CompleteResponse.ok(meta, "合并完成，共 " + session.totalChunks() + " 个分片");
            } catch (Exception e) {
                return CompleteResponse.fail(List.of(), "合并失败: " + e.getMessage());
            }
        }
    }

    /** 演示接口：随机翻转某个已上传分片中间 1 个字节，模拟“磁盘位翻转/传输损坏” */
    public ChunkResult corruptRandomChunk(String uploadId) throws IOException {
        Path dir = requireUploadDir(uploadId);
        List<Integer> indexes = listChunkIndexes(dir);
        if (indexes.isEmpty()) {
            throw new IllegalArgumentException("当前会话没有已上传的分片可破坏");
        }
        int pick = indexes.get(ThreadLocalRandom.current().nextInt(indexes.size()));
        Path chunk = dir.resolve(String.valueOf(pick));
        byte[] data = Files.readAllBytes(chunk);
        data[data.length / 2] ^= 0x5A;
        Files.write(chunk, data);
        return new ChunkResult(false, pick, null, "已人为破坏分片 " + pick + " 的 1 个字节");
    }

    /* ==================== 2. 小文件批量上传 ==================== */

    /**
     * 一次 HTTP 请求携带多个小文件，服务端逐个落盘并逐个返回结果：
     * 单个文件失败不影响同批其他文件。
     */
    public List<BatchItemResult> batchUpload(MultipartFile[] files) throws IOException {
        ensureDirs();
        List<BatchItemResult> results = new ArrayList<>();
        for (MultipartFile f : files) {
            String raw = f.getOriginalFilename() == null ? "unnamed" : f.getOriginalFilename();
            String name = Paths.get(raw).getFileName().toString();
            if (name.isEmpty() || !SAFE_NAME.matcher(name).matches()) {
                results.add(new BatchItemResult(raw, false, null, "非法文件名"));
                continue;
            }
            try (InputStream in = f.getInputStream()) {
                FileMeta meta = storeFile(name, in, f.getSize());
                results.add(new BatchItemResult(name, true, meta.id(), "上传成功"));
            } catch (Exception e) {
                results.add(new BatchItemResult(name, false, null, "上传失败: " + e.getMessage()));
            }
        }
        return results;
    }

    /* ==================== 3. 文件登记 / 查询 ==================== */

    public List<FileMeta> listFiles() throws IOException {
        ensureDirs();
        List<FileMeta> list = new ArrayList<>();
        try (Stream<Path> list2 = Files.list(fileDir)) {
            list2.filter(p -> p.getFileName().toString().endsWith(".meta.json")).forEach(p -> {
                try {
                    list.add(om.readValue(p.toFile(), FileMeta.class));
                } catch (IOException ignored) {
                    // 单个元数据损坏不影响列表
                }
            });
        }
        list.sort(Comparator.comparing(FileMeta::uploadTime).reversed());
        return list;
    }

    public FileMeta getMeta(String id) throws IOException {
        requireFile(id);
        return om.readValue(fileDir.resolve(id + ".meta.json").toFile(), FileMeta.class);
    }

    public Path resolveFile(String id) {
        return requireFile(id);
    }

    /* ==================== 内部工具 ==================== */

    /** 存储目录自愈：目录被删除后各接口仍可用 */
    private void ensureDirs() throws IOException {
        Files.createDirectories(chunkDir);
        Files.createDirectories(fileDir);
    }

    private FileMeta storeFile(String name, InputStream in, long size) throws IOException {
        String id = UUID.randomUUID().toString().replace("-", "");
        Path target = fileDir.resolve(id + ".bin");
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(target), 1 << 16)) {
            in.transferTo(out);
        }
        FileMeta meta = new FileMeta(id, name, size, null, LocalDateTime.now().withNano(0).toString());
        om.writeValue(fileDir.resolve(id + ".meta.json").toFile(), meta);
        return meta;
    }

    /** 分片健康 = 文件存在 + 大小与 meta 一致 + 重新计算的 MD5 与 meta 一致 */
    private boolean chunkHealthy(Path dir, int index) {
        Path chunk = dir.resolve(String.valueOf(index));
        Path metaPath = dir.resolve(index + ".meta.json");
        if (!Files.isRegularFile(chunk) || !Files.isRegularFile(metaPath)) {
            return false;
        }
        try {
            ChunkMeta meta = readChunkMeta(dir, index);
            if (Files.size(chunk) != meta.size()) {
                return false;
            }
            try (InputStream in = Files.newInputStream(chunk)) {
                return Md5Util.digest(in).equals(meta.md5());
            }
        } catch (Exception e) {
            return false;
        }
    }

    private UploadSession readSession(Path dir) throws IOException {
        return om.readValue(dir.resolve("upload.json").toFile(), UploadSession.class);
    }

    private ChunkMeta readChunkMeta(Path dir, int index) throws IOException {
        return om.readValue(dir.resolve(index + ".meta.json").toFile(), ChunkMeta.class);
    }

    private List<Integer> listChunkIndexes(Path dir) throws IOException {
        List<Integer> idx = new ArrayList<>();
        try (Stream<Path> list = Files.list(dir)) {
            list.map(p -> p.getFileName().toString())
                    .filter(n -> n.chars().allMatch(Character::isDigit))
                    .map(Integer::parseInt)
                    .sorted()
                    .forEach(idx::add);
        }
        return idx;
    }

    private Path requireUploadDir(String uploadId) throws IOException {
        if (uploadId == null || !SAFE_ID.matcher(uploadId).matches()) {
            throw new IllegalArgumentException("非法 uploadId");
        }
        Path dir = chunkDir.resolve(uploadId);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("上传会话不存在，请先调用 /api/upload/init 初始化");
        }
        return dir;
    }

    private Path requireFile(String id) {
        if (id == null || !SAFE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("非法文件 ID");
        }
        Path f = fileDir.resolve(id + ".bin");
        if (!Files.isRegularFile(f)) {
            throw new IllegalArgumentException("文件不存在");
        }
        return f;
    }

    private void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }
}
