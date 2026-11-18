Add-Type -AssemblyName System.Drawing

$iconDir = Join-Path $PSScriptRoot "src-tauri\icons"
$sourceIcon = "$iconDir\icon.png"
$outputIco = "$iconDir\icon.ico"

# Regenerar todos os PNGs a partir do icon.png base
$allSizes = @{
    "16x16.png" = 16
    "24x24.png" = 24
    "32x32.png" = 32
    "48x48.png" = 48
    "128x128.png" = 128
    "128x128@2x.png" = 256
}

$srcImg = [System.Drawing.Image]::FromFile($sourceIcon)

foreach ($file in $allSizes.Keys) {
    $size = $allSizes[$file]
    $destPath = "$iconDir\$file"
    
    $bmp = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bmp)
    $graphics.Clear([System.Drawing.Color]::Transparent)
    $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceOver
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    
    $destRect = New-Object System.Drawing.Rectangle(0, 0, $size, $size)
    $srcRect = New-Object System.Drawing.Rectangle(0, 0, $srcImg.Width, $srcImg.Height)
    $graphics.DrawImage($srcImg, $destRect, $srcRect, [System.Drawing.GraphicsUnit]::Pixel)
    $graphics.Dispose()
    
    $bmp.Save($destPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    
    Write-Host "Criado: $file ($size x $size)"
}

$srcImg.Dispose()

# Criar ICO com múltiplos tamanhos
$sizes = @(16, 24, 32, 48, 256)
$pngFiles = @(
    "$iconDir\16x16.png",
    "$iconDir\24x24.png", 
    "$iconDir\32x32.png",
    "$iconDir\48x48.png",
    "$iconDir\128x128@2x.png"
)

$pngData = @()
for ($i = 0; $i -lt $sizes.Count; $i++) {
    $img = [System.Drawing.Image]::FromFile($pngFiles[$i])
    $ms = New-Object System.IO.MemoryStream
    $img.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    $pngData += ,@{
        Width = if ($sizes[$i] -ge 256) { 0 } else { $sizes[$i] }
        Height = if ($sizes[$i] -ge 256) { 0 } else { $sizes[$i] }
        Bytes = $ms.ToArray()
    }
    $ms.Dispose()
    $img.Dispose()
}

$fs = [System.IO.File]::Create($outputIco)
$bw = New-Object System.IO.BinaryWriter($fs)

$bw.Write([UInt16]0)
$bw.Write([UInt16]1)
$bw.Write([UInt16]$pngData.Count)

$offset = 6 + ($pngData.Count * 16)

foreach ($png in $pngData) {
    $bw.Write([byte]$png.Width)
    $bw.Write([byte]$png.Height)
    $bw.Write([byte]0)
    $bw.Write([byte]0)
    $bw.Write([UInt16]1)
    $bw.Write([UInt16]32)
    $bw.Write([UInt32]$png.Bytes.Length)
    $bw.Write([UInt32]$offset)
    $offset += $png.Bytes.Length
}

foreach ($png in $pngData) {
    $bw.Write($png.Bytes)
}

$bw.Close()
$fs.Close()

Write-Host ""
Write-Host "ICO criado com tamanhos: 16, 24, 32, 48, 256 px"
Get-Item $outputIco | Select-Object Name, Length
