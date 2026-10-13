#!/usr/bin/env bash
# 敏感信息扫描：阻止明确不应提交的内容进入仓库。
#
# 拦截范围：
#   1. 本地环境文件、私钥和证书文件本身；
#   2. 高置信度的密钥特征（私钥块、云厂商与平台令牌、JWT 形态的字符串）。
#
# 已知边界：仓库中既有的内网测试环境 MySQL/Redis 连接配置由仓库所有者明确保留
#   （见 plan/progress.md 记录的决定），不在本脚本拦截范围内；
#   该例外不能作为放宽扫描或提交新凭证的理由。
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

status=0

forbidden="$(git ls-files \
    | grep -E '(^|/)\.env($|\.)|\.(pem|p12|jks|keystore|key)$|(^|/)id_(rsa|dsa|ecdsa|ed25519)$' \
    | grep -vE '(^|/)\.env\.example$' || true)"
if [ -n "$forbidden" ]; then
    echo "以下文件不应被跟踪：" >&2
    echo "$forbidden" >&2
    status=1
fi

patterns=(
    'BEGIN (RSA |OPENSSH |EC |DSA |PGP )?PRIVATE KEY'
    'AKIA[0-9A-Z]{16}'
    'ASIA[0-9A-Z]{16}'
    'gh[pousr]_[A-Za-z0-9]{36,}'
    'github_pat_[A-Za-z0-9_]{22,}'
    'xox[abprs]-[A-Za-z0-9-]{10,}'
    'AIza[0-9A-Za-z_-]{35}'
    'eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}'
)

for pattern in "${patterns[@]}"; do
    hits="$(git grep -InE "$pattern" -- . ':(exclude)scripts/check-secrets.sh' 2>/dev/null || true)"
    if [ -n "$hits" ]; then
        echo "匹配到疑似敏感信息（模式：$pattern）：" >&2
        echo "$hits" >&2
        status=1
    fi
done

if [ "$status" -eq 0 ]; then
    echo "敏感信息扫描通过：未发现私钥、平台令牌或本地环境文件。"
fi

exit "$status"