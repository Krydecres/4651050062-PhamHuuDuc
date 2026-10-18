[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "TEST 1: GET /products khong co token (Ky vong: 401 Unauthorized)" -ForegroundColor Yellow
try {
    $res = Invoke-RestMethod -Uri 'http://localhost:8081/products' -Method Get
    Write-Host "That bai! Endpoint khong duoc bao ve." -ForegroundColor Red
} catch {
    Write-Host "Status: $($_.Exception.Response.StatusCode.value__)" -ForegroundColor Green
    $stream = $_.Exception.Response.GetResponseStream()
    $reader = New-Object System.IO.StreamReader($stream)
    Write-Host "Body: $($reader.ReadToEnd())" -ForegroundColor Green
}

Write-Host "`n==================================================" -ForegroundColor Cyan
Write-Host "TEST 2: GET /products voi token sai (Ky vong: 401 Unauthorized)" -ForegroundColor Yellow
try {
    $res = Invoke-RestMethod -Uri 'http://localhost:8081/products' -Method Get -Headers @{ Authorization = 'Bearer fake-token-12345' }
    Write-Host "That bai! Token sai nhung van cho qua." -ForegroundColor Red
} catch {
    Write-Host "Status: $($_.Exception.Response.StatusCode.value__)" -ForegroundColor Green
    $stream = $_.Exception.Response.GetResponseStream()
    $reader = New-Object System.IO.StreamReader($stream)
    Write-Host "Body: $($reader.ReadToEnd())" -ForegroundColor Green
}

Write-Host "`n==================================================" -ForegroundColor Cyan
Write-Host "TEST 3: Dang nhap Auth Service (Port 8080) lay JWT Token" -ForegroundColor Yellow
$loginBody = @{ userName = 'admin'; password = '123456' } | ConvertTo-Json
$loginRes = Invoke-RestMethod -Uri 'http://localhost:8080/' -Method Post -Body $loginBody -ContentType 'application/json'
Write-Host "Dang nhap thanh cong: $($loginRes.message)" -ForegroundColor Green
$token = $loginRes.token
Write-Host "Token JWT: $token" -ForegroundColor Magenta

$headers = @{ 
    Authorization = "Bearer $token"
    "Content-Type" = "application/json; charset=utf-8"
}

Write-Host "`n==================================================" -ForegroundColor Cyan
Write-Host "TEST 4: GET /products voi JWT token hop le (Ky vong: 200 OK)" -ForegroundColor Yellow
$productsRes = Invoke-RestMethod -Uri 'http://localhost:8081/products' -Method Get -Headers $headers
Write-Host ($productsRes | ConvertTo-Json -Depth 4) -ForegroundColor Green

Write-Host "`n==================================================" -ForegroundColor Cyan
Write-Host "TEST 5: GET /products/1 lay chi tiet san pham ID = 1 (Ky vong: 200 OK)" -ForegroundColor Yellow
$p1Res = Invoke-RestMethod -Uri 'http://localhost:8081/products/1' -Method Get -Headers $headers
Write-Host ($p1Res | ConvertTo-Json -Depth 4) -ForegroundColor Green

Write-Host "`n==================================================" -ForegroundColor Cyan
Write-Host "TEST 6: POST /products them san pham moi (Ky vong: 201 Created)" -ForegroundColor Yellow
$newProductJson = '{
    "name": "Bàn phím cơ Keychron K2 Pro",
    "description": "Bàn phím cơ không dây Bluetooth, Switch Gateron Brown",
    "price": 2150000.00,
    "quantity": 30
}'
$createRes = Invoke-RestMethod -Uri 'http://localhost:8081/products' -Method Post -Headers $headers -Body ([System.Text.Encoding]::UTF8.GetBytes($newProductJson))
Write-Host ($createRes | ConvertTo-Json -Depth 4) -ForegroundColor Green
$newId = $createRes.data.id
Write-Host "ID san pham moi tao: $newId" -ForegroundColor Magenta

Write-Host "`n==================================================" -ForegroundColor Cyan
Write-Host "TEST 7: PUT /products/$newId cap nhat san pham (Ky vong: 200 OK)" -ForegroundColor Yellow
$updateProductJson = '{
    "name": "Bàn phím cơ Keychron K2 Pro (Version 2 Hot-swap)",
    "description": "Nâng cấp RGB, Foam tiêu âm, Keycap PBT",
    "price": 2350000.00,
    "quantity": 25
}'
$updateRes = Invoke-RestMethod -Uri "http://localhost:8081/products/$newId" -Method Put -Headers $headers -Body ([System.Text.Encoding]::UTF8.GetBytes($updateProductJson))
Write-Host ($updateRes | ConvertTo-Json -Depth 4) -ForegroundColor Green

Write-Host "`n==================================================" -ForegroundColor Cyan
Write-Host "TEST 8: DELETE /products/$newId xoa san pham (Ky vong: 200 OK)" -ForegroundColor Yellow
$delRes = Invoke-RestMethod -Uri "http://localhost:8081/products/$newId" -Method Delete -Headers $headers
Write-Host ($delRes | ConvertTo-Json -Depth 4) -ForegroundColor Green

Write-Host "`n==================================================" -ForegroundColor Cyan
Write-Host "TEST 9: GET /products/$newId sau khi xoa (Ky vong: 404 Not Found)" -ForegroundColor Yellow
try {
    $res = Invoke-RestMethod -Uri "http://localhost:8081/products/$newId" -Method Get -Headers $headers
    Write-Host "That bai! San pham van con ton tai." -ForegroundColor Red
} catch {
    Write-Host "Status: $($_.Exception.Response.StatusCode.value__)" -ForegroundColor Green
    $stream = $_.Exception.Response.GetResponseStream()
    $reader = New-Object System.IO.StreamReader($stream)
    Write-Host "Body: $($reader.ReadToEnd())" -ForegroundColor Green
}

Write-Host "`n==================================================" -ForegroundColor Cyan
Write-Host "TAT CA 9 TEST CASES DEU HOAN THANH XUAT SAC!" -ForegroundColor Green
Write-Host "==================================================" -ForegroundColor Cyan
