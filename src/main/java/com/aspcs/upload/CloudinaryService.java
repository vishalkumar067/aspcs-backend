package com.aspcs.upload;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;

    @Value("${cloudinary.cloud-name:dug0g6tli}")
    private String cloudName;

    public String uploadImage(MultipartFile file, String folder) throws IOException {
        log.info("Uploading image to Cloudinary folder: {}", folder);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "resource_type", "image"
                    )
            );
            String url = (String) result.get("secure_url");
            log.info("Uploaded to Cloudinary: {}", url);
            return url;
        } catch (IOException e) {
            log.error("Cloudinary upload failed: {}", e.getMessage());
            throw e;
        }
    }

    public String uploadFile(MultipartFile file, String folder) throws IOException {
        log.info("Uploading file to Cloudinary folder: {}", folder);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                        "folder", folder,
                        "resource_type", "raw"
                )
        );
        return (String) result.get("secure_url");
    }

    // For server-generated files (e.g. PDFs built in-memory) that don't
    // arrive as a MultipartFile from a user upload.
    public String uploadBytes(byte[] bytes, String folder, String publicId) throws IOException {
        log.info("Uploading generated file to Cloudinary folder: {} (publicId={})", folder, publicId);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = cloudinary.uploader().upload(
                bytes,
                ObjectUtils.asMap(
                        "folder", folder,
                        "public_id", publicId,
                        "resource_type", "raw",
                        "overwrite", true
                )
        );
        return (String) result.get("secure_url");
    }

    public void deleteFile(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            log.info("Deleted from Cloudinary: {}", publicId);
        } catch (IOException e) {
            log.warn("Failed to delete from Cloudinary: {}", e.getMessage());
        }
    }
}
