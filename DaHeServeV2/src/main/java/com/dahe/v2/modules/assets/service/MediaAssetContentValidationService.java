package com.dahe.v2.modules.assets.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 上传内容校验服务。
 *
 * <p>负责内容级类型判断（魔数/MIME/图片可读性）并输出规范化结果。</p>
 */
public interface MediaAssetContentValidationService {

    /**
     * 对上传二进制文件做内容级校验并返回检测结果。
     *
     * @param file 上传文件
     * @param originalFileName 客户端原始文件名
     * @return 检测结果（资源类型 + 存储扩展名）
     * @throws IllegalArgumentException 校验失败时抛出
     */
    DetectionResult validateForUpload(MultipartFile file, String originalFileName);

    /**
     * 上传内容检测结果。
     */
    class DetectionResult {
        private final String fileType;
        private final String storageExtension;

        public DetectionResult(String fileType, String storageExtension) {
            this.fileType = fileType;
            this.storageExtension = storageExtension;
        }

        public String getFileType() {
            return fileType;
        }

        public String getStorageExtension() {
            return storageExtension;
        }
    }
}
