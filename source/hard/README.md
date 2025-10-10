# Hard Scripts (Full Content)

Thư mục này chứa các **script đặc biệt** có **config phức tạp** - app sẽ tải **toàn bộ nội dung** thay vì wrap loadstring.

## Cơ chế hoạt động

### 📦 Scripts thông thường (từ nguồn khác)
```json
{
  "name": "BlueX Hub",
  "url": "https://raw.githubusercontent.com/Dev-BlueX/BlueX-Hub/refs/heads/main/Main.lua"
}
```
**→ App tạo file .txt với nội dung:**
```lua
loadstring(game:HttpGet("https://raw.githubusercontent.com/Dev-BlueX/BlueX-Hub/refs/heads/main/Main.lua"))()
```

### 🔧 Scripts đặc biệt (trong `/source/hard/`)
```json
{
  "name": "Teddy Hub Kaitun",
  "url": "https://raw.githubusercontent.com/RenjiYuusei/Kasumi/main/source/hard/teddy-kaitun.lua"
}
```
**→ App tải toàn bộ file .lua (bao gồm config + loadstring)**

## Khi nào dùng `/source/hard/`?

✅ **Dùng khi script có**:
- Config phức tạp (getgenv, settings, ...)
- Cần người dùng tùy chỉnh trước khi chạy
- Nhiều options và parameters

❌ **KHÔNG dùng khi**:
- Script đơn giản (chỉ 1 dòng loadstring)
- Từ nguồn external (GitHub của người khác)

## Ví dụ script trong `/source/hard/`

```lua
-- teddy-kaitun.lua
repeat wait() until game:IsLoaded() and game.Players.LocalPlayer
getgenv().AutoExecute = true 
getgenv().SettingFarm = {
    ["White Screen"] = false,
    ["Lock Fps"] = {
        ["Enabled"] = true,
        ["FPS"] = 360,
    },
    -- ... nhiều config khác
}
loadstring(game:HttpGet("URL_SCRIPT_CHÍNH"))()
```

Người dùng có thể edit file `.txt` sau khi tải để thay đổi config!
