# 生成nginx本地自签证书（dev only；产物已被.gitignore，不入库——红线4）
# 用法：powershell -File gen-cert.ps1   （需openssl在PATH，Git for Windows自带）
$ErrorActionPreference = 'Stop'
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
$certs = Join-Path $dir 'certs'
New-Item -ItemType Directory -Force -Path $certs | Out-Null
openssl req -x509 -nodes -newkey rsa:2048 -days 365 `
  -keyout (Join-Path $certs 'aies.key') `
  -out (Join-Path $certs 'aies.crt') `
  -subj "/CN=localhost" `
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"
Write-Host "OK: $certs\aies.crt / aies.key"
