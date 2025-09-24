# Keep rules placeholder. Add rules if needed.

# Giữ thông tin generic và annotation cho Gson/Retrofit/OkHttp
-keepattributes Signature
-keepattributes *Annotation*

# Giữ lớp dữ liệu parse bằng Gson (tránh đổi tên field)
-keep class com.cloudphone.tool.PreloadApp { *; }

# (Tuỳ chọn) Nếu sau này parse ApkItem bằng Gson, giữ luôn
#-keep class com.cloudphone.tool.ApkItem { *; }

# Giữ các lớp nội bộ của Gson (thường không bắt buộc, nhưng an toàn)
-keep class com.google.gson.stream.** { *; }

# Ẩn cảnh báo không ảnh hưởng build
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**
