# Генератор моделей пластинок для ресурс-паку (формат 1.21.1).
#
# На 1.21.1 компонент custom_model_data ще ЧИСЛОВИЙ, а вибір моделі робиться через
# "overrides" з предикатом custom_model_data у самій моделі предмета — рядкових ключів і
# assets/minecraft/items/*.json (minecraft:select) тут ще немає, вони з'явились у 1.21.4.
#
# Скрипт бере читабельні ключі (id з custom-items.yml + сталі ключі здібностей, Характеристик
# і монет), рахує для кожного те саме число, що й me.vangoo.infrastructure.items.ItemModelData
# (Java String.hashCode), і перезаписує assets/minecraft/models/item/music_disc_*.json.
#
# Запуск (з кореня репозиторію):
#   powershell -ExecutionPolicy Bypass -File tools/resourcepack/rp-item-models.gen.ps1
#
# Звіряє результат із плагіном тест ResourcePackItemModelTest (mvn test).

param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'

$pack      = Join-Path $RepoRoot 'mysteries-resourcepack\assets\minecraft'
$modelsDir = Join-Path $pack 'models\item'
$configYml = Join-Path $RepoRoot 'src\main\resources\custom-items.yml'

# ВАЖЛИВО: та сама функція, що в ItemModelData.of(String) — Math.floorMod(hashCode, 9_000_000) + 1_000_000.
function Get-ModelData([string]$key) {
    $h = 0L
    foreach ($ch in $key.ToCharArray()) {
        $h = (($h * 31) + [int64][int][char]$ch) -band 0xFFFFFFFFL
    }
    if ($h -ge 2147483648L) { $h -= 4294967296L }   # назад у знаковий 32-бітний int
    $m = $h % 9000000L
    if ($m -lt 0) { $m += 9000000L }
    return [int]($m + 1000000L)
}

# Читає custom-items.yml простим сканом (плаский, передбачуваний формат: material + custom-model-data).
function Read-CustomItems([string]$path) {
    $items = @()
    $material = $null
    $key = $null
    foreach ($line in Get-Content -LiteralPath $path -Encoding UTF8) {
        if ($line -match '^\s{2}([a-z0-9_]+):\s*$') {
            if ($material -and $key) { $items += [pscustomobject]@{ Material = $material; Key = $key } }
            $material = $null; $key = $null
        }
        elseif ($line -match '^\s+material:\s*([A-Z0-9_]+)\s*$') { $material = $Matches[1] }
        elseif ($line -match '^\s+custom-model-data:\s*"([^"]+)"\s*$') { $key = $Matches[1] }
    }
    if ($material -and $key) { $items += [pscustomobject]@{ Material = $material; Key = $key } }
    return $items
}

# Ключі, яких немає в custom-items.yml: їх ставить код, а не конфіг.
# Формат: матеріал = @{ ключ = ім'я моделі в models/item }.
$staticKeys = @{
    'MUSIC_DISC_WARD'    = [ordered]@{      # AbilityItemFactory
        'active'             = 'active'
        'passive'            = 'passive'
        'permanent_passive'  = 'permanent_passive'
    }
    'MUSIC_DISC_CHIRP'   = [ordered]@{ 'characteristic' = 'music_disc_chirp_custom_w7yj' }  # CharacteristicCodec
    'MUSIC_DISC_MELLOHI' = [ordered]@{ 'gold_pound' = 'pound' }                             # CurrencyCodec
    'MUSIC_DISC_STAL'    = [ordered]@{ 'coppet' = 'coppet' }                                # CurrencyCodec
}

# Збираємо ключ → модель по матеріалах.
$byMaterial = @{}
function Add-Key([string]$material, [string]$key, [string]$model) {
    if (-not $byMaterial.ContainsKey($material)) { $byMaterial[$material] = [ordered]@{} }
    $byMaterial[$material][$key] = $model
}

foreach ($item in Read-CustomItems $configYml) {
    if ($item.Material -notlike 'MUSIC_DISC_*') { continue }   # ENCHANTED_BOOK книги рецептів тощо
    Add-Key $item.Material $item.Key $item.Key
}
foreach ($material in $staticKeys.Keys) {
    foreach ($key in $staticKeys[$material].Keys) {
        Add-Key $material $key $staticKeys[$material][$key]
    }
}

$skipped = @()
foreach ($material in ($byMaterial.Keys | Sort-Object)) {
    $disc = $material.ToLowerInvariant()
    $entries = @()
    foreach ($key in $byMaterial[$material].Keys) {
        $model = $byMaterial[$material][$key]
        if (-not (Test-Path -LiteralPath (Join-Path $modelsDir "$model.json"))) {
            # Моделі ще не намалювали — предмет законно лишається ванільною пластинкою.
            $skipped += "$key ($material)"
            continue
        }
        $entries += [pscustomobject]@{ Data = (Get-ModelData $key); Model = "item/$model"; Key = $key }
    }
    if ($entries.Count -eq 0) { continue }

    $duplicates = $entries | Group-Object Data | Where-Object { $_.Count -gt 1 }
    if ($duplicates) {
        throw "Колізія custom_model_data у ${material}: " + (($duplicates | ForEach-Object { $_.Group.Key -join '/' }) -join ', ')
    }

    # Порядок ОБОВ'ЯЗКОВО за зростанням: ванільний предикат числовий і матчить '>=',
    # виграє ОСТАННІЙ збіг у списку. За зростанням це рівно власне значення предмета.
    $overrides = $entries | Sort-Object Data | ForEach-Object {
        "    { `"predicate`": { `"custom_model_data`": $($_.Data) }, `"model`": `"$($_.Model)`" }"
    }

    $json = @"
{
  "parent": "minecraft:item/template_music_disc",
  "textures": {
    "layer0": "minecraft:item/$disc"
  },
  "overrides": [
$($overrides -join ",`n")
  ]
}
"@
    $target = Join-Path $modelsDir "$disc.json"
    [System.IO.File]::WriteAllText($target, $json + "`n", (New-Object System.Text.UTF8Encoding($false)))
    Write-Host ("{0}: {1} override(s)" -f "$disc.json", $entries.Count)
}

if ($skipped.Count -gt 0) {
    Write-Host ""
    Write-Host ("Без моделі (лишаються ванільними): {0}" -f $skipped.Count) -ForegroundColor Yellow
}
