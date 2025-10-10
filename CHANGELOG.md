# Changelog

## [1.1.1] - 2025-10-10
### 🎯 Chức năng mới
- **Hỗ trợ XAPK**: Thêm khả năng cài đặt file XAPK (cùng với APK và APKS đã hỗ trợ)
  - Tự động phát hiện và giải nén file XAPK
  - Cài đặt split APK từ file XAPK qua root hoặc cách thường
  - Tương thích với các file từ APKPure, APKMirror và các nguồn khác

### 🐛 Sửa lỗi
- **Clear cache**: Sửa lỗi nút xóa cache không xóa hết - giờ đã xóa cả thư mục splits đã giải nén
- **Log gọn gàng**: Loại bỏ các log ENV trùng lặp, giảm nhiễu trong nhật ký
- **XAPK parsing**: Cải thiện logic giải nén và sắp xếp APK từ XAPK

## [1.1.0] - 2025-09-30 (Update 2)

### ✨ Cải tiến giao diện
- **Material Design 3**: Áp dụng Material You với màu sắc hiện đại
- **Theme tối nâng cao**: Giao diện tối mượt mà hơn với gradient và shadow
- **Icon cho tabs**: Thêm icon trực quan cho các tab Ứng dụng, Đã cài đặt, Nhật ký
- **Card design mới**: Bo góc 16dp, stroke outline, elevation tối ưu
- **Thanh tìm kiếm cải tiến**: Outlined style với icon search và clear button

### 🎯 Chức năng mới
- **Sắp xếp đa dạng**:
  - Tên A-Z / Z-A
  - Kích thước file (lớn → nhỏ)
  - Ngày tải xuống (mới → cũ)
- **Badge "Đã tải"**: Hiển thị trạng thái cache với badge màu
- **Hiển thị kích thước file**: Xem dung lượng APK đã cache (MB/GB)
- **Thống kê cache**: Thanh stats hiển thị tổng số app và dung lượng cache
- **Quản lý cache**: Nút xóa toàn bộ cache với thống kê chi tiết
- **Progress indicator**: Màu sắc đồng nhất theo theme

### 🔧 Cải tiến kỹ thuật
- Tối ưu hiển thị danh sách với RecyclerView
- Format file size chính xác (B/KB/MB/GB)
- Sort performance được tối ưu
- Code structure rõ ràng hơn với enum SortMode

### 🎨 UI/UX
- Button style Material 3 (Tonal, Outlined, Text)
- Icon buttons với ripple effect
- Spacing và padding đồng nhất
- Color contrast tốt hơn cho dark theme
- Typography cải tiến

### 🐛 Sửa lỗi (Update 2)
- ✅ Sửa lỗi tab Nhật ký không hiển thị đúng
- ✅ Thiết kế lại icon app với gradient Purple Material You
- ✅ Thêm adaptive icon cho Android 8.0+
- ✅ Icon hiện đại với phone + cloud + download arrow

## [1.0.1] - Previous version
- Cài đặt APK từ URL
- Hỗ trợ root installation
- Quản lý ứng dụng đã cài đặt
