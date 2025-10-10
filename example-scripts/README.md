# Example Scripts

Thư mục này chứa các script mẫu để người dùng tham khảo.

## Cách sử dụng

### Script có config phức tạp (như Teddy Kaitun)

**Bước 1**: Copy file `teddy-kaitun.txt` vào điện thoại:
```
/storage/emulated/0/Delta/Autoexecute/teddy-kaitun.txt
```
hoặc
```
/storage/emulated/0/Delta/Scripts/teddy-kaitun.txt
```

**Bước 2**: Mở file và chỉnh sửa config theo ý muốn:
```lua
getgenv().SettingFarm = {
    ["White Screen"] = false,  -- Đổi thành true nếu muốn
    ["Lock Fps"] = {
        ["Enabled"] = true,
        ["FPS"] = 360,  -- Thay đổi FPS
    },
    ...
}
```

**Bước 3**: Lưu file và app sẽ tự động detect script

## Hoặc thêm vào scripts.json

Nếu muốn chia sẻ script qua app:

1. Upload file `.lua` lên GitHub (bao gồm cả config)
2. Thêm vào `source/scripts.json`:
```json
{
  "name": "Teddy Hub Kaitun",
  "gameName": "Blox Fruits",
  "url": "https://raw.githubusercontent.com/YOUR_USER/YOUR_REPO/main/teddy-kaitun.lua"
}
```

## Lưu ý

- Script với config nên để người dùng tự tạo file local để tùy chỉnh
- Script đơn giản (chỉ loadstring) thì thêm vào scripts.json
