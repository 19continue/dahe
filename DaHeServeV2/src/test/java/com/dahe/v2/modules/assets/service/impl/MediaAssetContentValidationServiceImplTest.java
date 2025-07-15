package com.dahe.v2.modules.assets.service.impl;

import com.dahe.v2.modules.assets.domain.AssetDomainConstants;
import com.dahe.v2.modules.assets.service.MediaAssetContentValidationService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

class MediaAssetContentValidationServiceImplTest {

    private final MediaAssetContentValidationService service = new MediaAssetContentValidationServiceImpl();

    @Test
    void validateForUpload_shouldDetectPngByMagicAndReturnImage() {
        byte[] pngBytes = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+X2e0AAAAASUVORK5CYII="
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.dat",
                "application/octet-stream",
                pngBytes
        );

        MediaAssetContentValidationService.DetectionResult result = service.validateForUpload(file, "avatar.dat");

        Assertions.assertEquals(AssetDomainConstants.FILE_TYPE_IMAGE, result.getFileType());
        Assertions.assertEquals(AssetDomainConstants.EXT_PNG, result.getStorageExtension());
    }

    @Test
    void validateForUpload_shouldRejectFakeJpgContent() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fake.jpg",
                "image/jpeg",
                "not-image-content".getBytes(StandardCharsets.UTF_8)
        );

        IllegalArgumentException error = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.validateForUpload(file, "fake.jpg")
        );

        Assertions.assertTrue(error.getMessage().contains("图片"), "错误信息应提示图片内容非法");
    }

    @Test
    void validateForUpload_shouldKeepFileTypeForTextFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "readme.txt",
                "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8)
        );

        MediaAssetContentValidationService.DetectionResult result = service.validateForUpload(file, "readme.txt");

        Assertions.assertEquals(AssetDomainConstants.FILE_TYPE_FILE, result.getFileType());
        Assertions.assertEquals(".txt", result.getStorageExtension());
    }
}
