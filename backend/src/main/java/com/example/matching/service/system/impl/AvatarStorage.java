package com.example.matching.service.system.impl;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/**
 * 头像文件存储。
 *
 * <p>抽成独立组件而不是把 IO 写进 {@code SysUserServiceImpl}，理由是两件事正交：
 * 「账号资料」是领域逻辑，「文件落盘」是基础设施。分开后单测账号逻辑时不必碰磁盘，
 * 也便于将来整体换成对象存储（只替换本类实现）。</p>
 *
 * <p>存储方式与学习资源封面保持一致：写入运行目录下的 {@code uploads/avatars}，
 * 经 {@code WebStaticResourceConfig} 的 {@code /uploads/**} 映射对外可读。
 * 返回的是**访问路径**而非绝对路径 —— 数据库里存绝对路径会让换机器/换容器后头像全失效。</p>
 */
@Slf4j
@Component
public class AvatarStorage {

    /** 头像落盘目录（相对应用运行目录），与 /uploads/** 静态映射对应 */
    static final String AVATAR_UPLOAD_ROOT = "uploads/avatars";

    /** 对外访问前缀 */
    static final String AVATAR_URL_PREFIX = "/uploads/avatars/";

    /** 允许的图片类型 -> 扩展名。与资源封面一致，头像同样是浏览器直出的静态图片 */
    private static final Map<String, String> IMAGE_EXT_BY_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/gif", ".gif",
            "image/webp", ".webp");

    /** 头像体积上限：2MB。头像只在顶栏/个人中心小尺寸展示，过大的原图没有必要 */
    private static final int MAX_AVATAR_BYTES = 2 * 1024 * 1024;

    /**
     * 保存头像并返回可访问路径。
     *
     * @param content     文件字节
     * @param contentType 浏览器上报的 MIME 类型
     * @return 形如 {@code /uploads/avatars/xxxx.png} 的访问路径
     */
    public String store(byte[] content, String contentType) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "上传文件不能为空");
        }
        if (content.length > MAX_AVATAR_BYTES) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "头像不能超过 2MB");
        }
        String ext = contentType == null ? null : IMAGE_EXT_BY_TYPE.get(contentType.toLowerCase());
        // 白名单校验：不按扩展名（可伪造）而按 MIME 决定落盘后缀，
        // 避免上传 .html/.svg 之类可执行内容被当图片直出
        if (ext == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "仅支持 JPG/PNG/GIF/WebP 格式的头像");
        }
        try {
            Path uploadDir = Paths.get(AVATAR_UPLOAD_ROOT);
            Files.createDirectories(uploadDir);
            String filename = UUID.randomUUID().toString().replace("-", "") + ext;
            Files.write(uploadDir.resolve(filename), content);
            log.info("头像上传成功: file={}, size={}", filename, content.length);
            return AVATAR_URL_PREFIX + filename;
        } catch (IOException e) {
            log.error("头像保存失败: error={}", e.getMessage(), e);
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "头像保存失败: " + e.getMessage());
        }
    }

    /**
     * 是否为本站托管的头像路径。
     *
     * <p>用于删除旧头像时做安全判断：只删自己目录下的文件，
     * 防止传入 {@code ../../xxx} 之类路径穿越，或误删外部 URL。</p>
     */
    public boolean isManaged(String avatarPath) {
        return avatarPath != null && avatarPath.startsWith(AVATAR_URL_PREFIX);
    }

    /** 删除本站托管的头像文件；失败只记日志（旧头像残留不影响业务） */
    public void deleteQuietly(String avatarPath) {
        if (!isManaged(avatarPath)) {
            return;
        }
        String filename = avatarPath.substring(AVATAR_URL_PREFIX.length());
        if (filename.isBlank() || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            log.warn("头像路径不合法，跳过删除: {}", avatarPath);
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(AVATAR_UPLOAD_ROOT).resolve(filename));
        } catch (IOException e) {
            log.warn("旧头像删除失败（忽略）: file={}, error={}", filename, e.getMessage());
        }
    }
}
