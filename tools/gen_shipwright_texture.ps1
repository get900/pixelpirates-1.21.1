# Pixel Pirates — shipwright table textures (top / side / bottom)
# Run:  powershell -ExecutionPolicy Bypass -File tools\gen_shipwright_texture.ps1
# Binary PNG output via System.Drawing — no BOM risk.

Add-Type -AssemblyName System.Drawing

$blockDir = "D:\Minecraft Modding\pixel-pirates-1.21.X\src\main\resources\assets\pixelpirates\textures\block"
New-Item -ItemType Directory -Force -Path $blockDir | Out-Null

function C([int]$r, [int]$g, [int]$b) { return [System.Drawing.Color]::FromArgb(255, $r, $g, $b) }
function New-Canvas16 { return New-Object System.Drawing.Bitmap(16, 16, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb) }
function Save-Png($bmp, [string]$name) {
    $bmp.Save((Join-Path $blockDir ($name + ".png")), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Output ("wrote " + $name + ".png")
}
function Mottle($bmp, $rand, $c0, $c1, $c2) {
    for ($x = 0; $x -lt 16; $x++) { for ($y = 0; $y -lt 16; $y++) {
        $v = $rand.NextDouble()
        $c = $c0; if ($v -ge 0.55) { $c = $c1 }; if ($v -ge 0.85) { $c = $c2 }
        $bmp.SetPixel($x, $y, $c)
    } }
}

# Palette
$wood      = C 122 88 52     # oak-ish tabletop wood
$woodDark  = C 100 70 40
$woodLight = C 140 104 64
$frame     = C 82 56 32      # dark frame / seams
$paper     = C 226 214 178   # aged blueprint parchment
$paperDim  = C 212 199 160
$ink       = C 52 82 128     # blueprint ink
$inkLight  = C 90 122 168
$nail      = C 60 60 66
$iron      = C 148 152 160

# ============ TOP — parchment blueprint pinned to a wood tabletop ============
$r = New-Object System.Random(7101); $b = New-Canvas16
Mottle $b $r $wood $woodDark $woodLight
# parchment sheet (2..13 x 3..12), slightly torn corner
for ($x = 2; $x -le 13; $x++) { for ($y = 3; $y -le 12; $y++) {
    $c = $paper; if ($r.NextDouble() -lt 0.18) { $c = $paperDim }
    $b.SetPixel($x, $y, $c)
} }
$b.SetPixel(13, 3, $wood)  # torn corner
# pin nails
$b.SetPixel(2, 3, $nail); $b.SetPixel(13, 12, $nail); $b.SetPixel(2, 12, $nail)
# blueprint: ship hull profile (keel arc + deck line)
foreach ($p in @(@(4,9), @(5,10), @(6,10), @(7,10), @(8,10), @(9,10), @(10,10), @(11,9))) {
    $b.SetPixel($p[0], $p[1], $ink)
}
foreach ($p in @(@(4,8), @(11,8), @(5,8), @(6,8), @(7,8), @(8,8), @(9,8), @(10,8))) {
    $b.SetPixel($p[0], $p[1], $inkLight)
}
# mast + rigging
for ($y = 4; $y -le 7; $y++) { $b.SetPixel(8, $y, $ink) }
$b.SetPixel(6, 5, $inkLight); $b.SetPixel(7, 5, $inkLight)   # sail edge
$b.SetPixel(9, 5, $inkLight); $b.SetPixel(10, 6, $inkLight)  # stay line
# measure marks along the paper bottom
foreach ($x in @(4, 6, 8, 10)) { $b.SetPixel($x, 12, $inkLight) }
Save-Png $b "shipwright_table_top"

# ============ SIDE — planked cabinet with iron band + hanging saw ============
$r = New-Object System.Random(7102); $b = New-Canvas16
Mottle $b $r $wood $woodDark $woodLight
# tabletop lip (top two rows darker)
for ($x = 0; $x -lt 16; $x++) { $b.SetPixel($x, 0, $frame); $b.SetPixel($x, 1, $woodDark) }
# vertical plank seams
foreach ($x in @(3, 7, 11)) { for ($y = 2; $y -lt 16; $y++) { if ($r.NextDouble() -lt 0.85) { $b.SetPixel($x, $y, $frame) } } }
# iron band across the middle with rivets
for ($x = 0; $x -lt 16; $x++) { $b.SetPixel($x, 8, $iron) }
foreach ($x in @(1, 5, 9, 13)) { $b.SetPixel($x, 8, $nail) }
# hanging saw silhouette (right side)
for ($y = 3; $y -le 6; $y++) { $b.SetPixel(13, $y, $iron) }
$b.SetPixel(14, 3, $iron); $b.SetPixel(14, 4, $iron)
$b.SetPixel(13, 7, $nail)
# bottom shadow row
for ($x = 0; $x -lt 16; $x++) { $b.SetPixel($x, 15, $frame) }
Save-Png $b "shipwright_table_side"

# ============ BOTTOM — plain dark planks ============
$r = New-Object System.Random(7103); $b = New-Canvas16
Mottle $b $r $woodDark $frame $wood
foreach ($y in @(4, 8, 12)) { for ($x = 0; $x -lt 16; $x++) { if ($r.NextDouble() -lt 0.8) { $b.SetPixel($x, $y, $frame) } } }
Save-Png $b "shipwright_table_bottom"

Write-Output "done."
