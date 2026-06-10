package com.aspcs.upload;

import com.aspcs.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class UploadController {

    private final CloudinaryService cloudinaryService;

    private static final List<String> ALLOWED_TYPES = List.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );
    private static final long MAX_SIZE = 10 * 1024 * 1024; // 10MB

    // ─── Upload single image ─────────────────────────────────────────────────
    @PostMapping("/image")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<CloudinaryService.UploadResult>> uploadImage(
            @RequestParam("file")   MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "general") String folder
    ) throws IOException {
        validateFile(file);
        CloudinaryService.UploadResult result = cloudinaryService.uploadImage(file, folder);
        return ResponseEntity.ok(ApiResponse.ok(result, "Image uploaded successfully"));
    }

    // ─── Upload multiple images ──────────────────────────────────────────────
    @PostMapping("/images")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<List<CloudinaryService.UploadResult>>> uploadImages(
            @RequestParam("files")  List<MultipartFile> files,
            @RequestParam(value = "folder", defaultValue = "gallery") String folder
    ) throws IOException {
        List<CloudinaryService.UploadResult> results = new ArrayList<>();
        for (MultipartFile file : files) {
            validateFile(file);
            results.add(cloudinaryService.uploadImage(file, folder));
        }
        return ResponseEntity.ok(ApiResponse.ok(results, results.size() + " images uploaded"));
    }

    // ─── Delete image ────────────────────────────────────────────────────────
    @DeleteMapping("/image")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteImage(
            @RequestParam("publicId") String publicId
    ) throws IOException {
        cloudinaryService.deleteImage(publicId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Image deleted successfully"));
    }

    // ─── Validation ─────────────────────────────────────────────────────────
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException(
                    "Invalid file type. Allowed: JPEG, PNG, WebP, GIF");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("File too large. Max size is 10MB");
        }
    }
}
