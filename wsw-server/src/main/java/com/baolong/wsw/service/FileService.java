package com.baolong.wsw.service;

import com.baolong.wsw.common.BizException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传：只做最朴素的本地磁盘存储。
 * <p>存储根目录是 {@code user.dir/uploads}（见 {@code WebMvcConfig} 的静态映射），
 * 按 {@code yyyyMM} 分目录，文件名用 UUID，避免中文名与重名问题。
 */
@Service
public class FileService {

    private static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "gif", "webp", "pdf");

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("请选择要上传的文件");
        }
        if (file.getSize() > MAX_SIZE) {
            throw BizException.badRequest("文件不能超过 5MB");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0 && dot < original.length() - 1) {
            ext = original.substring(dot + 1).toLowerCase(Locale.ROOT);
        }
        if (!ALLOWED.contains(ext)) {
            throw BizException.badRequest("只支持 jpg/png/gif/webp/pdf");
        }

        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = Paths.get(System.getProperty("user.dir"), "uploads", month);
        try {
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(name).toFile());
        } catch (IOException e) {
            throw BizException.badRequest("文件保存失败：" + e.getMessage());
        }
        return "/files/upload/" + month + "/" + name;
    }
}
