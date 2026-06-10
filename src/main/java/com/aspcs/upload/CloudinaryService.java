package com.aspcs.upload;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;

    // ─── Upload image ────────────────────────────────────────────────────────
    public UploadResult uploadImage(MultipartFile file, String folder) throws IOException {
        Map<?, ?> result = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                        "folder",          "aspcs/" + folder,
                        "resource_type",   "image",
                        "transformation",  "q_auto,f_auto"
                )
        );

        return new UploadResult(
                (String) result.get("public_id"),
                (String) result.get("secure_url"),
                buildThumbnailUrl((String) result.get("public_id")),
                ((Number) result.get("width")).intValue(),
                ((Number) result.get("height")).intValue(),
                ((Number) result.get("bytes")).longValue()
        );
    }

    // ─── Delete image ────────────────────────────────────────────────────────
    public void deleteImage(String publicId) throws IOException {
        cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        log.info("Deleted image: {}", publicId);
    }

    // ─── Build thumbnail URL ─────────────────────────────────────────────────
    private String buildThumbnailUrl(String publicId) {
        return cloudinary.url()
                .transformation(new com.cloudinary.Transformation()
                        .width(400).height(300).crop("fill").quality("auto"))
                .generate(publicId);
    }

    // ─── Upload Result DTO ───────────────────────────────────────────────────
    public record UploadResult(
            String publicId,
            String url,
            String thumbnailUrl,
            int    width,
            int    height,
            long   bytes
    ) {}
}
