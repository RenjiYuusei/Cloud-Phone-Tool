# Source Files

Thư mục này chứa các file JSON để app tải từ GitHub.

## apps.json
Danh sách ứng dụng APK để cài đặt.

## scripts.json
Danh sách scripts Lua cho Delta Executor.

### Cách tạo scripts.json

File phải có định dạng đơn giản:

```json
[
  {
    "name": "Tên script",
    "gameName": "Tên game/Universal",
    "url": "https://raw.githubusercontent.com/.../script.lua"
  }
]
```

**✅ CHỈ CẦN URL THUẦN**:
- Field `url` chỉ cần chứa **URL đến file .lua**, không cần `loadstring`
- App sẽ tự động wrap URL trong `loadstring(game:HttpGet("..."))()` khi lưu vào file .txt
- Người dùng chọn thư mục (Auto-execute/Scripts) khi tải, không cần field `type`

### Ví dụ script hợp lệ:

```json
[
  {
    "name": "BlueX Hub",
    "gameName": "Blox Fruits",
    "url": "https://raw.githubusercontent.com/Dev-BlueX/BlueX-Hub/refs/heads/main/Main.lua"
  },
  {
    "name": "Maru Hub Premium",
    "gameName": "Universal",
    "url": "https://raw.githubusercontent.com/hnc-roblox/Free/refs/heads/main/MaruHubPremiumFake.HNC%20Roblox.lua"
  },
  {
    "name": "Infinite Yield",
    "gameName": "Universal",
    "url": "https://raw.githubusercontent.com/EdgeIY/infiniteyield/master/source"
  }
]
```

### Khi người dùng tải script:
1. Chọn script từ danh sách
2. Nhấn "Tải xuống"
3. Chọn thư mục: **Auto-execute** hoặc **Scripts**
4. App tự động tạo file `.txt` với nội dung:
   ```lua
   loadstring(game:HttpGet("URL"))()
   ```

### Sau khi upload lên GitHub

App sẽ tự động tải từ URL:
```
https://raw.githubusercontent.com/RenjiYuusei/Kasumi/main/source/scripts.json
```

Người dùng có thể fork repo và đổi URL trong `MainActivity.kt`:
```kotlin
private val DEFAULT_SCRIPTS_URL = "YOUR_GITHUB_RAW_URL/scripts.json"
```

## Thêm script mới vào scripts.json

Chỉ cần thêm 1 dòng mới theo định dạng:
```json
{
  "name": "Script Name",
  "gameName": "Game Name",
  "url": "https://raw.githubusercontent.com/.../script.lua"
}
```

**Ví dụ thêm script mới**:
```json
[
  {
    "name": "BlueX Hub",
    "gameName": "Blox Fruits",
    "url": "https://raw.githubusercontent.com/Dev-BlueX/BlueX-Hub/refs/heads/main/Main.lua"
  },
  {
    "name": "NEW SCRIPT",
    "gameName": "NEW GAME",
    "url": "https://raw.githubusercontent.com/USER/REPO/main/script.lua"
  }
]
```

Rất dễ dàng để thêm scripts mới!
