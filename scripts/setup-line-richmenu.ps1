# 建立並套用 LINE Rich Menu：「本月」「上月」「其他月份」（其他月份觸發 Datetime Picker）。
# Datetime Picker action 無法在 LINE Official Account Manager 後台設定，必須透過 Messaging API 建立。
# 執行一次即可（重跑會先刪除同名舊選單再重建）：
#   powershell -File scripts\setup-line-richmenu.ps1
# Channel access token 依序取自 -Token 參數、環境變數 LINE_CHANNEL_TOKEN、專案根目錄 .env。
param(
    [string]$Token
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$MenuName = '195Home-month-query'

if (-not $Token) { $Token = $env:LINE_CHANNEL_TOKEN }
if (-not $Token) {
    $envFile = Join-Path $PSScriptRoot '..\.env'
    if (Test-Path $envFile) {
        $line = Get-Content $envFile -Encoding UTF8 | Where-Object { $_ -match '^\s*LINE_CHANNEL_TOKEN\s*=' } | Select-Object -First 1
        if ($line) { $Token = ($line -split '=', 2)[1].Trim() }
    }
}
if (-not $Token) { throw '找不到 LINE_CHANNEL_TOKEN（-Token、環境變數或 .env）' }

$headers = @{ Authorization = "Bearer $Token" }

# 1. 產生選單圖片（2500x843，三等分）
$width = 2500; $height = 843
$labels = @('本月', '上月', '其他月份')
$subtitles = @('當月支出匯總', '上個月支出匯總', '選擇任意月份')
$colorPairs = @(@('#2BD46B', '#06A94D'), @('#4FB0F0', '#2B7FD8'), @('#FFB45C', '#F2762E'))
$imagePath = Join-Path ([IO.Path]::GetTempPath()) 'richmenu-195home.png'
$bmp = New-Object System.Drawing.Bitmap $width, $height
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$g.Clear([System.Drawing.ColorTranslator]::FromHtml('#F3F5F7'))
$fontTitle = New-Object System.Drawing.Font('Microsoft JhengHei', 100, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$fontSub = New-Object System.Drawing.Font('Microsoft JhengHei', 46, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$center = New-Object System.Drawing.StringFormat
$center.Alignment = [System.Drawing.StringAlignment]::Center
$center.LineAlignment = [System.Drawing.StringAlignment]::Center
$white = [System.Drawing.Brushes]::White
$subBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(215, 255, 255, 255))
$glassBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(60, 255, 255, 255))
$pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::White), 9
$pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
$pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round

function New-RoundedRect([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $p.AddArc($x, $y, $d, $d, 180, 90)
    $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $p.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $p.CloseFigure()
    return $p
}

$cellWidths = @(833, 833, 834)
$pad = 26
$x = 0
for ($i = 0; $i -lt 3; $i++) {
    $cardX = $x + $pad; $cardY = $pad; $cardW = $cellWidths[$i] - 2 * $pad; $cardH = $height - 2 * $pad
    $cardRect = New-Object System.Drawing.RectangleF $cardX, $cardY, $cardW, $cardH
    $c1 = [System.Drawing.ColorTranslator]::FromHtml($colorPairs[$i][0])
    $c2 = [System.Drawing.ColorTranslator]::FromHtml($colorPairs[$i][1])
    $grad = New-Object System.Drawing.Drawing2D.LinearGradientBrush $cardRect, $c1, $c2, 55
    $path = New-RoundedRect $cardX $cardY $cardW $cardH 64
    $g.FillPath($grad, $path)

    # 圖示：半透明圓底 + 日曆
    $cx = $x + $cellWidths[$i] / 2
    $circleD = 230; $circleY = 175
    $g.FillEllipse($glassBrush, $cx - $circleD / 2, $circleY, $circleD, $circleD)
    $cal = New-RoundedRect ($cx - 66) ($circleY + 52) 132 124 18
    $g.DrawPath($pen, $cal)
    $g.DrawLine($pen, $cx - 66, $circleY + 92, $cx + 66, $circleY + 92)
    $g.DrawLine($pen, $cx - 30, $circleY + 36, $cx - 30, $circleY + 62)
    $g.DrawLine($pen, $cx + 30, $circleY + 36, $cx + 30, $circleY + 62)
    $midY = $circleY + 134
    if ($i -eq 0) {
        $g.FillEllipse($white, $cx - 17, $midY - 17, 34, 34)
    } elseif ($i -eq 1) {
        $g.DrawLines($pen, @(
            (New-Object System.Drawing.PointF ($cx + 12), ($midY - 22)),
            (New-Object System.Drawing.PointF ($cx - 12), $midY),
            (New-Object System.Drawing.PointF ($cx + 12), ($midY + 22))))
    } else {
        foreach ($dx in -30, 0, 30) { $g.FillEllipse($white, $cx + $dx - 10, $midY - 10, 20, 20) }
    }

    $g.DrawString($labels[$i], $fontTitle, $white, (New-Object System.Drawing.RectangleF $cardX, 445, $cardW, 140), $center)
    $g.DrawString($subtitles[$i], $fontSub, $subBrush, (New-Object System.Drawing.RectangleF $cardX, 590, $cardW, 80), $center)
    $x += $cellWidths[$i]
}
$bmp.Save($imagePath, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()

# 2. 刪除同名舊選單
$existing = Invoke-RestMethod -Uri 'https://api.line.me/v2/bot/richmenu/list' -Headers $headers
foreach ($menu in $existing.richmenus | Where-Object { $_.name -eq $MenuName }) {
    Invoke-RestMethod -Method Delete -Uri "https://api.line.me/v2/bot/richmenu/$($menu.richMenuId)" -Headers $headers | Out-Null
}

# 3. 建立選單：本月／上月以相對 data 表示，由 notification-service 換算實際月份
$menuJson = @{
    size        = @{ width = $width; height = $height }
    selected    = $true
    name        = $MenuName
    chatBarText = '查詢支出'
    areas       = @(
        @{ bounds = @{ x = 0;    y = 0; width = 833; height = $height }
           action = @{ type = 'postback'; label = '本月'; data = 'month=current' } },
        @{ bounds = @{ x = 833;  y = 0; width = 833; height = $height }
           action = @{ type = 'postback'; label = '上月'; data = 'month=previous' } },
        @{ bounds = @{ x = 1666; y = 0; width = 834; height = $height }
           action = @{ type = 'datetimepicker'; label = '其他月份'; data = 'month=pick'; mode = 'date' } }
    )
} | ConvertTo-Json -Depth 6
$created = Invoke-RestMethod -Method Post -Uri 'https://api.line.me/v2/bot/richmenu' -Headers $headers `
    -ContentType 'application/json; charset=UTF-8' -Body ([Text.Encoding]::UTF8.GetBytes($menuJson))
$richMenuId = $created.richMenuId

# 4. 上傳圖片並設為所有使用者的預設選單
Invoke-RestMethod -Method Post -Uri "https://api-data.line.me/v2/bot/richmenu/$richMenuId/content" -Headers $headers `
    -ContentType 'image/png' -InFile $imagePath | Out-Null
Invoke-RestMethod -Method Post -Uri "https://api.line.me/v2/bot/user/all/richmenu/$richMenuId" -Headers $headers | Out-Null

Write-Host "Rich Menu 已套用：$richMenuId"
