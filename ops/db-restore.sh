#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || ! -f "$1" ]]; then
  printf '用法: %s <备份.sql.gz>\n' "$0" >&2
  exit 2
fi

backup_file="$1"
: "${DB_HOST:=127.0.0.1}"
: "${DB_PORT:=3306}"
: "${DB_NAME:=simp_issue_hub}"
: "${DB_USERNAME:?请设置 DB_USERNAME}"
: "${DB_PASSWORD:?请设置 DB_PASSWORD}"

if [[ -f "$backup_file.sha256" ]]; then
  sha256sum --check "$backup_file.sha256"
fi
printf '恢复将覆盖数据库 %s@%s:%s。输入 RESTORE 继续: ' "$DB_NAME" "$DB_HOST" "$DB_PORT"
read -r confirmation
[[ "$confirmation" == RESTORE ]] || { printf '已取消。\n'; exit 1; }

export MYSQL_PWD="$DB_PASSWORD"
trap 'unset MYSQL_PWD' EXIT
gunzip -c -- "$backup_file" | mariadb --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" "$DB_NAME"
printf '恢复完成。请启动应用并确认 Flyway 状态与 /api/v1/health。\n'
