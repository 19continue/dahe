package com.dahe.v2.modules.assets.service.impl;

import com.dahe.v2.modules.assets.domain.AssetDomainConstants;
import com.dahe.v2.modules.assets.service.MediaAssetContentValidationService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;

/**
 * 上传内容校验实现。
 *
 * <p>说明：</p>
 * <p>1. 先读取头部魔数，避免仅靠扩展名判断类型；</p>
 * <p>2. 对常见图片格式执行解码可读性校验；</p>
 * <p>3. WEBP 在 JDK8 默认 ImageIO 下可能无解码器，因此采用严格魔数校验。</p>
 */
@Service
public class MediaAssetContentValidationServiceImpl implements MediaAssetContentValidationService {

    private static final int HEADER_MAX_BYTES = 16;
    private static final String MIME_IMAGE_PREFIX = "image/";
    private static final String MIME_JPEG = "image/jpeg";
    private static final String MIME_PNG = "image/png";
    private static final String MIME_GIF = "image/gif";
    private static final String MIME_WEBP = "image/webp";
    private static final String MIME_BMP = "image/bmp";

    @Override
    public DetectionResult validateForUpload(MultipartFile file, String originalFileName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        String ext = normalizeExtension(extractExtension(originalFileName));
        String mime = normalizeLower(file.getContentType());
        byte[] header = readHeader(file);
        String magicExt = detectMagicExtension(header);

        boolean extImage = AssetDomainConstants.IMAGE_EXTENSIONS.contains(ext);
        boolean magicImage = AssetDomainConstants.IMAGE_EXTENSIONS.contains(magicExt);
        boolean mimeImage = mime.startsWith(MIME_IMAGE_PREFIX);
        boolean candidateImage = extImage || magicImage || mimeImage;
        String readableImageExt = detectReadableImageExtension(file);

        if (!candidateImage) {
            return new DetectionResult(AssetDomainConstants.FILE_TYPE_FILE, sanitizeStorageExtension(ext));
        }

        String resolvedImageExt = resolveImageExtension(ext, magicExt, mime);
        if (AssetDomainConstants.EXT_WEBP.equals(resolvedImageExt)) {
            if (!AssetDomainConstants.EXT_WEBP.equals(magicExt)) {
                if (StringUtils.hasText(readableImageExt)) {
                    return new DetectionResult(AssetDomainConstants.FILE_TYPE_IMAGE, sanitizeStorageExtension(readableImageExt));
                }
                throw new IllegalArgumentException("图片文件内容与扩展名不匹配");
            }
            return new DetectionResult(AssetDomainConstants.FILE_TYPE_IMAGE, AssetDomainConstants.EXT_WEBP);
        }

        if (!StringUtils.hasText(readableImageExt) && !StringUtils.hasText(magicExt)) {
            verifyImageDecodable(file);
        }
        String finalImageExt = StringUtils.hasText(magicExt)
                ? magicExt
                : (StringUtils.hasText(readableImageExt) ? readableImageExt : resolvedImageExt);
        return new DetectionResult(AssetDomainConstants.FILE_TYPE_IMAGE, sanitizeStorageExtension(finalImageExt));
    }

    private byte[] readHeader(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            byte[] buffer = new byte[HEADER_MAX_BYTES];
            int read = input.read(buffer);
            if (read <= 0) {
                return new byte[0];
            }
            if (read >= HEADER_MAX_BYTES) {
                return buffer;
            }
            return Arrays.copyOf(buffer, read);
        } catch (IOException e) {
            throw new IllegalArgumentException("读取文件头失败");
        }
    }

    private void verifyImageDecodable(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new IllegalArgumentException("图片文件内容无效或格式不受支持");
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("图片文件内容校验失败");
        }
    }

    private String detectReadableImageExtension(MultipartFile file) {
        try (InputStream input = file.getInputStream();
             ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) {
                return "";
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                return "";
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                return mapImageFormatToExtension(reader.getFormatName());
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    private String mapImageFormatToExtension(String formatName) {
        String format = normalizeLower(formatName);
        if ("jpeg".equals(format) || "jpg".equals(format)) {
            return AssetDomainConstants.EXT_JPG;
        }
        if ("png".equals(format)) {
            return AssetDomainConstants.EXT_PNG;
        }
        if ("gif".equals(format)) {
            return AssetDomainConstants.EXT_GIF;
        }
        if ("bmp".equals(format)) {
            return AssetDomainConstants.EXT_BMP;
        }
        if ("webp".equals(format)) {
            return AssetDomainConstants.EXT_WEBP;
        }
        return "";
    }

    private String detectMagicExtension(byte[] header) {
        if (header == null || header.length < 3) {
            return "";
        }
        if (startsWith(header, new int[]{0xFF, 0xD8, 0xFF})) {
            return AssetDomainConstants.EXT_JPG;
        }
        if (startsWith(header, new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) {
            return AssetDomainConstants.EXT_PNG;
        }
        if (startsWithAscii(header, "GIF87a") || startsWithAscii(header, "GIF89a")) {
            return AssetDomainConstants.EXT_GIF;
        }
        if (startsWithAscii(header, "BM")) {
            return AssetDomainConstants.EXT_BMP;
        }
        if (isWebp(header)) {
            return AssetDomainConstants.EXT_WEBP;
        }
        return "";
    }

    private boolean isWebp(byte[] header) {
        if (header == null || header.length < 12) {
            return false;
        }
        return startsWithAscii(header, "RIFF") && equalsAscii(header, 8, "WEBP");
    }

    private boolean startsWith(byte[] source, int[] prefix) {
        if (source == null || prefix == null || source.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((source[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean startsWithAscii(byte[] source, String text) {
        return equalsAscii(source, 0, text);
    }

    private boolean equalsAscii(byte[] source, int offset, String text) {
        if (source == null || text == null || offset < 0) {
            return false;
        }
        if (source.length < offset + text.length()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if ((char) source[offset + i] != text.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private String resolveImageExtension(String ext, String magicExt, String mime) {
        if (StringUtils.hasText(magicExt)) {
            return normalizeExtension(magicExt);
        }
        if (AssetDomainConstants.IMAGE_EXTENSIONS.contains(ext)) {
            return ext;
        }
        if (MIME_JPEG.equals(mime)) {
            return AssetDomainConstants.EXT_JPG;
        }
        if (MIME_PNG.equals(mime)) {
            return AssetDomainConstants.EXT_PNG;
        }
        if (MIME_GIF.equals(mime)) {
            return AssetDomainConstants.EXT_GIF;
        }
        if (MIME_WEBP.equals(mime)) {
            return AssetDomainConstants.EXT_WEBP;
        }
        if (MIME_BMP.equals(mime)) {
            return AssetDomainConstants.EXT_BMP;
        }
        return AssetDomainConstants.EXT_JPG;
    }

    private String sanitizeStorageExtension(String ext) {
        String normalized = normalizeExtension(ext);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        if (normalized.length() > 12) {
            return "";
        }
        for (int i = 1; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            boolean valid = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
            if (!valid) {
                return "";
            }
        }
        return normalized;
    }

    private String extractExtension(String fileName) {
        String normalized = String.valueOf(fileName == null ? "" : fileName).trim();
        int idx = normalized.lastIndexOf('.');
        if (idx < 0 || idx == normalized.length() - 1) {
            return "";
        }
        return normalized.substring(idx);
    }

    private String normalizeExtension(String ext) {
        String text = String.valueOf(ext == null ? "" : ext).trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        if (!text.startsWith(".")) {
            text = "." + text;
        }
        return text;
    }

    private String normalizeLower(String raw) {
        return String.valueOf(raw == null ? "" : raw).trim().toLowerCase(Locale.ROOT);
    }
}
