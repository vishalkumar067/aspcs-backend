════════════════════════════════════════════════════════
  ASPCS Backend Fix - Exact File Replacement Guide
════════════════════════════════════════════════════════

STEP 1 — DELETE this file (causes duplicate class errors):
─────────────────────────────────────────────────────────
  DELETE:  src/main/java/com/aspcs/student/StudentModule.java
  (Your project already has Student.java, StudentRepository.java etc. separately)


STEP 2 — REPLACE these files (fix Lombok + other errors):
──────────────────────────────────────────────────────────

  File in this zip                                  │  Replace in your project
  ─────────────────────────────────────────────────────────────────────────────
  auth/entity/AdminUser.java                        │  src/main/java/com/aspcs/auth/entity/AdminUser.java
  common/ApiResponse.java                           │  src/main/java/com/aspcs/common/ApiResponse.java
  common/GlobalExceptionHandler.java                │  src/main/java/com/aspcs/common/GlobalExceptionHandler.java
  notice/NoticeModule.java                          │  src/main/java/com/aspcs/notice/NoticeModule.java
  gallery/GalleryModule.java                        │  src/main/java/com/aspcs/gallery/GalleryModule.java
  tc/TcModule.java                                  │  src/main/java/com/aspcs/tc/TcModule.java
  career/CareerModule.java                          │  src/main/java/com/aspcs/career/CareerModule.java
  admissions/AdmissionsModule.java                  │  src/main/java/com/aspcs/admissions/AdmissionsModule.java
  upload/CloudinaryService.java                     │  src/main/java/com/aspcs/upload/CloudinaryService.java


STEP 3 — Fix AuthService.java manually:
──────────────────────────────────────────────────────────
  Open: src/main/java/com/aspcs/auth/AuthService.java
  Add @Slf4j annotation above the class declaration:

    @Slf4j           ← ADD THIS LINE
    @Service
    @RequiredArgsConstructor
    public class AuthService { ...


STEP 4 — Fix JwtAuthFilter.java manually:
──────────────────────────────────────────────────────────
  Open: src/main/java/com/aspcs/security/JwtAuthFilter.java
  Add @Slf4j annotation above the class declaration:

    @Slf4j           ← ADD THIS LINE
    @Component
    public class JwtAuthFilter extends OncePerRequestFilter { ...


STEP 5 — Push to GitHub:
──────────────────────────────────────────────────────────
  git add .
  git commit -m "Fix Lombok annotations and duplicate class errors"
  git push

Railway will rebuild automatically. Build should succeed this time.


WHAT WAS FIXED IN EACH FILE:
─────────────────────────────
  AdminUser.java         → Added @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
                           Added getPassword() override (was missing from UserDetails impl)

  ApiResponse.java       → Added @Builder so builder() method works

  GlobalExceptionHandler → Added @Slf4j so log variable works

  NoticeModule.java      → Notice entity: added @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor

  GalleryModule.java     → GalleryAlbum + GalleryImage: added full Lombok annotations

  TcModule.java          → TcRequest entity: added full Lombok annotations

  CareerModule.java      → JobListing + JobApplication: added full Lombok annotations

  AdmissionsModule.java  → AdmissionInquiry entity: added full Lombok annotations

  CloudinaryService.java → Added @Slf4j so log variable works
