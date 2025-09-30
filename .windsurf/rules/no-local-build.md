---
trigger: always_on
---

# CI-only Build Policy (No Local Build)

- Không chạy, không gợi ý và không yêu cầu cài đặt/bật bất kỳ lệnh build cục bộ nào:
  - Cấm: gradle/gradlew (./gradlew, gradlew.bat), java, sdkmanager, adb, bundletool...
  - Không gợi ý cài Java, Android SDK, Gradle trên máy người dùng.

- Tất cả build APK phải chạy trên GitHub Actions:
  - Workflow dùng: [.github/workflows/build-release.yml](cci:7://file:///c:/Users/Administrator/Downloads/VTU-Translate-Tool/.github/workflows/build-release.yml:0:0-0:0)
  - Triggers: push vào `main/master/develop` hoặc `workflow_dispatch`.

- Khi người dùng yêu cầu “build/run”:
  - Hướng dẫn: commit/push để kích hoạt workflow, hoặc vào GitHub UI -> Actions -> chạy workflow bằng “Run workflow”.
  - Không chạy lệnh build trên máy người dùng. Không yêu cầu thiết lập môi trường cục bộ.

- Hành động được phép:
  - Chỉnh sửa mã nguồn, cấu hình CI, commit/push.
  - Hướng dẫn kiểm tra trạng thái build trong GitHub Actions, tải artifact APK từ mục Artifacts.

- Hành động KHÔNG được phép:
  - Bất kỳ lệnh build cục bộ nào (ví dụ: `./gradlew assembleRelease`, `gradlew.bat`, `java -version`, `sdkmanager`, `adb`, v.v.).

- Đầu ra build:
  - Chỉ lấy từ GitHub Actions Artifacts (không tạo/chạy build trên máy người dùng).