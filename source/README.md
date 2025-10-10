-# Source Files

Thư mục này chứa các file JSON để app tải từ GitHub.

## 📱 apps.json
Danh sách ứng dụng APK để cài đặt.

## 📜 scripts.json
Danh sách scripts Lua cho Delta Executor.

---

## 🔧 Cách tạo scripts.json

### Định dạng cơ bản:
```json
[
  {
    "name": "Tên script",
    "gameName": "Tên game",
    "url": "https://raw.githubusercontent.com/.../script.lua"
  }
]
```

### 2 loại script:

#### 1️⃣ **Script thông thường** (từ nguồn khác)
```json
{
  "name": "BlueX Hub",
  "gameName": "Blox Fruits",
  "url": "https://raw.githubusercontent.com/Dev-BlueX/BlueX-Hub/refs/heads/main/Main.lua"
}
```

**Kết quả khi tải:**
```lua
loadstring(game:HttpGet("https://raw.githubusercontent.com/Dev-BlueX/BlueX-Hub/refs/heads/main/Main.lua"))()
```
→ App tự động wrap URL trong `loadstring`

---

#### 2️⃣ **Script đặc biệt** (có config, trong `/source/hard/`)
```json
{
  "name": "Teddy Hub Kaitun",
  "gameName": "Blox Fruits",
  "url": "https://raw.githubusercontent.com/RenjiYuusei/Kasumi/main/source/hard/teddy-kaitun.lua"
}
```

**Kết quả khi tải:**
→ App fetch **toàn bộ nội dung file** (bao gồm config + loadstring)

**Ví dụ nội dung:**
```lua
repeat wait() until game:IsLoaded()
getgenv().SettingFarm = {
    ["White Screen"] = false,
    ["FPS"] = 360,
    -- ... config
}
loadstring(game:HttpGet("URL_CHÍNH"))()
```

---

## 📝 Quy tắc:

### ✅ Script thông thường (wrap loadstring)
- URL không chứa `/source/hard/`
- Script từ nguồn external (GitHub khác)
- App tự động thêm `loadstring(game:HttpGet(...))()`

### 🔧 Script đặc biệt (fetch full content)
- URL chứa `/source/hard/`
- Script có config phức tạp
- App tải toàn bộ file từ GitHub
- Người dùng có thể edit file `.txt` để tùy chỉnh config

---

## 🚀 Thêm script mới

### Script đơn giản:
```json
{
  "name": "New Script",
  "gameName": "Game Name",
  "url": "https://raw.githubusercontent.com/USER/REPO/main/script.lua"
}
```

### Script có config:
1. Tạo file `.lua` trong `/source/hard/`
2. Thêm config + loadstring
3. Thêm vào `scripts.json` với URL đến file đó

---

## 🌐 URL app sử dụng:
```
https://raw.githubusercontent.com/RenjiYuusei/Kasumi/main/source/scripts.json
```
