#!/usr/bin/env bash
set -euo pipefail

if [[ $# -gt 1 ]]; then
  printf '用法: %s [输出目录]\n' "$0" >&2
  exit 2
fi

output_dir="${1:-./backups}"
mkdir -p -- "$output_dir"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
output_file="$output_dir/simp_issue_hub_${timestamp}.sql.gz"

: "${DB_HOST:=127.0.0.1}"
: "${DB_PORT:=3306}"
: "${DB_NAME:=simp_issue_hub}"
: "${DB_USERNAME:?请设置 DB_USERNAME}"
: "${DB_PASSWORD:?请设置 DB_PASSWORD}"

export MYSQL_PWD="$DB_PASSWORD"
trap 'unset MYSQL_PWD' EXIT

# 事务一致性快照；不把密码写入命令行参数或备份文件。
mariadb-dump --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" \
  --single-transaction --quick --routines --events --triggers \
  --hex-blob --set-gtid-purged=OFF "$DB_NAME" | gzip -9 > "$output_file"

sha256sum "$output_file" > "$output_file.sha256"
printf '已创建备份: %s\n校验文件: %s\n' "$output_file" "$output_file.sha256"
