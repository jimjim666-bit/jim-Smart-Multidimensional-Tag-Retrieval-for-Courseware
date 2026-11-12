# 接口冒烟测试：先把后端跑起来（8080），再在 backend 目录执行：
#   pwsh -File scripts\smoke-test.ps1
$ErrorActionPreference = "Stop"
$BASE = "http://localhost:8080"

$script:pass = 0
$script:fail = 0

function Check($name, $actual, $expect) {
    if ($actual -eq $expect) {
        Write-Host "[PASS] $name  code=$actual" -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host "[FAIL] $name  expect=$expect actual=$actual" -ForegroundColor Red
        $script:fail++
    }
}

function Post-Json($path, $body, $session) {
    if ($session) {
        Invoke-RestMethod "$BASE$path" -Method Post -ContentType "application/json;charset=UTF-8" -Body $body -WebSession $session
    } else {
        Invoke-RestMethod "$BASE$path" -Method Post -ContentType "application/json;charset=UTF-8" -Body $body
    }
}

function Get-Api($path, $session) {
    try {
        Invoke-WebRequest "$BASE$path" -WebSession $session -UseBasicParsing
    } catch {
        $_.Exception.Response
    }
}

# 1. 未登录访问受保护接口，应 401
$resp = Get-Api "/api/auth/me" $null
Check "未登录访问 me 返回 401" ([string]$resp.StatusCode.value__) "401"

# 2. 注册参数校验：账号太短
$r = Post-Json "/api/auth/register" '{"username":"ab","password":"12345678","nickname":"冒烟号"}'
Check "账号太短注册拦截" $r.code "PARAM_ERROR"

# 3. 注册参数校验：密码太短
$r = Post-Json "/api/auth/register" '{"username":"abcdef","password":"123","nickname":"冒烟号"}'
Check "密码太短注册拦截" $r.code "PARAM_ERROR"

# 4. 正常注册一个临时账号
$username = "smoke" + [DateTimeOffset]::Now.ToUnixTimeSeconds()
$body = "{`"username`":`"$username`",`"password`":`"12345678`",`"nickname`":`"冒烟号`"}"
$r = Post-Json "/api/auth/register" $body
Check "正常注册" $r.code "OK"

# 5. 重复注册
$r = Post-Json "/api/auth/register" $body
Check "重复注册拦截" $r.code "USERNAME_DUPLICATED"

# 6. 错误密码登录
$r = Post-Json "/api/auth/login" "{`"username`":`"$username`",`"password`":`"wrongpwd`"}"
Check "密码错误" $r.code "AUTH_BAD_CREDENTIALS"

# 7. 正确密码登录，保留会话
$login = Invoke-WebRequest "$BASE/api/auth/login" -Method Post -ContentType "application/json;charset=UTF-8" -Body "{`"username`":`"$username`",`"password`":`"12345678`"}" -SessionVariable sess -UseBasicParsing
$r = $login.Content | ConvertFrom-Json
Check "正确登录" $r.code "OK"

# 8. 带会话访问 me
$r = (Get-Api "/api/auth/me" $sess).Content | ConvertFrom-Json
Check "登录后访问 me" $r.code "OK"
Check "me 返回用户名" $r.data.username $username

# 9. 我的资源分页
$r = (Get-Api "/api/profile/my-resources?page=1&page_size=20" $sess).Content | ConvertFrom-Json
Check "我的资源分页" $r.code "OK"
Check "分页返回 list 字段" ($null -ne $r.data.list) "True"

# 10. 我的收藏分页
$r = (Get-Api "/api/profile/my-favorites?page=1&page_size=20" $sess).Content | ConvertFrom-Json
Check "我的收藏分页" $r.code "OK"
Check "收藏分页返回 list 字段" ($null -ne $r.data.list) "True"

# 11. 注销后再访问 me，应 401
Invoke-WebRequest "$BASE/api/auth/logout" -Method Post -WebSession $sess -UseBasicParsing | Out-Null
$resp = Get-Api "/api/auth/me" $sess
Check "注销后访问 me 返回 401" ([string]$resp.StatusCode.value__) "401"

Write-Host ""
Write-Host "通过 $script:pass  失败 $script:fail"
if ($script:fail -gt 0) { exit 1 }
