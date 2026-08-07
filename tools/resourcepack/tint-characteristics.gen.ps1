# Генерує тіновані текстури Характеристик для всіх 22 шляхів на основі сірої
# заготовки music_disc_chirp_custom_w7yj.png + кольору з me.vangoo.domain.PathwayBranding.
#
# Формула тону — така сама, як multiply-blend у графічному редакторі (і як уже застосована
# вручну до music_disc_chirp_error.png, звірено попіксельно): для кожного каналу
#   new = round(gray * brandColor / 255)
# де gray — значення каналу R сірої заготовки (в ній R=G=B), альфа переноситься як є.
#
# Пише для кожного шляху:
#   textures/item/music_disc_chirp_<name>.png(.mcmeta)
#   models/item/music_disc_chirp_<name>.json
# (<name> = pathwayName.ToLowerInvariant(), той самий стиль, що вже в music_disc_chirp_error.*)
#
# Після цього прогнати tools/resourcepack/rp-item-models.gen.ps1 — він перезбере
# overrides у music_disc_chirp.json на щойно створені моделі (rp-item-models.gen.ps1 знає
# per-pathway ключ characteristic_<Name> → модель music_disc_chirp_<name>).
#
# Запуск (з кореня репозиторію):
#   powershell -ExecutionPolicy Bypass -File tools/resourcepack/tint-characteristics.gen.ps1

param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$texturesDir = Join-Path $RepoRoot 'mysteries-resourcepack\assets\minecraft\textures\item'
$modelsDir   = Join-Path $RepoRoot 'mysteries-resourcepack\assets\minecraft\models\item'
$brandingSrc = Join-Path $RepoRoot 'src\main\java\me\vangoo\domain\PathwayBranding.java'

$basePngPath    = Join-Path $texturesDir 'music_disc_chirp_custom_w7yj.png'
$baseMcmetaPath = Join-Path $texturesDir 'music_disc_chirp_custom_w7yj.png.mcmeta'
$baseModelPath  = Join-Path $modelsDir   'music_disc_chirp_custom_w7yj.json'

# Єдине джерело кольору — PathwayBranding.java (put("<Name>", r, g, b, ChatColor.X)).
# Парситься регексом, щоб не дублювати таблицю кольорів і не розходитись з кодом.
$putPattern = [regex]'put\("([A-Za-z]+)",\s*(\d+),\s*(\d+),\s*(\d+),'
$pathways = @()
foreach ($line in Get-Content -LiteralPath $brandingSrc -Encoding UTF8) {
    $m = $putPattern.Match($line)
    if ($m.Success) {
        $pathways += [pscustomobject]@{
            Name = $m.Groups[1].Value
            R    = [int]$m.Groups[2].Value
            G    = [int]$m.Groups[3].Value
            B    = [int]$m.Groups[4].Value
        }
    }
}
if ($pathways.Count -eq 0) { throw "Не знайшов жодного put(...) у $brandingSrc" }
Write-Host "Знайдено $($pathways.Count) шляхів у PathwayBranding.java"

$baseMcmeta = Get-Content -LiteralPath $baseMcmetaPath -Raw -Encoding UTF8
$modelTemplate = Get-Content -LiteralPath $baseModelPath -Raw -Encoding UTF8

$baseBitmap = New-Object System.Drawing.Bitmap($basePngPath)
$width = $baseBitmap.Width
$height = $baseBitmap.Height

foreach ($p in $pathways) {
    $name = $p.Name.ToLowerInvariant()
    $texturePng    = Join-Path $texturesDir "music_disc_chirp_$name.png"
    $textureMcmeta = Join-Path $texturesDir "music_disc_chirp_$name.png.mcmeta"
    $modelJson     = Join-Path $modelsDir   "music_disc_chirp_$name.json"

    $tinted = New-Object System.Drawing.Bitmap($width, $height)
    for ($y = 0; $y -lt $height; $y++) {
        for ($x = 0; $x -lt $width; $x++) {
            $px = $baseBitmap.GetPixel($x, $y)
            if ($px.A -eq 0) {
                $tinted.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
                continue
            }
            $gray = $px.R
            $r = [Math]::Round($gray * $p.R / 255.0)
            $g = [Math]::Round($gray * $p.G / 255.0)
            $b = [Math]::Round($gray * $p.B / 255.0)
            $tinted.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($px.A, $r, $g, $b))
        }
    }
    $tinted.Save($texturePng, [System.Drawing.Imaging.ImageFormat]::Png)
    $tinted.Dispose()

    [System.IO.File]::WriteAllText($textureMcmeta, $baseMcmeta, (New-Object System.Text.UTF8Encoding($false)))

    $modelContent = $modelTemplate -replace 'minecraft:item/music_disc_chirp_custom_w7yj', "minecraft:item/music_disc_chirp_$name"
    [System.IO.File]::WriteAllText($modelJson, $modelContent, (New-Object System.Text.UTF8Encoding($false)))

    Write-Host ("{0} -> RGB({1},{2},{3})" -f $p.Name, $p.R, $p.G, $p.B)
}

$baseBitmap.Dispose()

Write-Host ""
Write-Host "Готово: $($pathways.Count) текстур+моделей. Далі прожени rp-item-models.gen.ps1, щоб перезібрати music_disc_chirp.json." -ForegroundColor Green
