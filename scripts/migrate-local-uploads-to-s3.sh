#!/usr/bin/env bash
# 로컬 저장소(UPLOAD_DIR, 운영은 EFS)의 업로드 이미지를 S3의 products/legacy/로 복사하고,
# DB에 저장된 /files/... 이미지 주소를 CDN 주소로 바꾸는 SQL을 만든다. SQL은 직접 실행하지 않는다.
#
# 사용법 (EFS를 마운트할 수 있고 버킷 쓰기 권한이 있는 곳에서):
#   UPLOAD_DIR=/mnt/uploads UPLOAD_S3_BUCKET=<버킷> UPLOAD_CDN_BASE_URL=https://<배포>.cloudfront.net \
#     scripts/migrate-local-uploads-to-s3.sh [--dry-run]
#
# 여러 번 실행해도 된다. 같은 파일은 같은 키로 다시 덮어쓴다.
# 절차는 docs/deployment-aws.md의 "업로드 이미지를 S3로 옮기기"를 따른다.
set -euo pipefail

: "${UPLOAD_DIR:?UPLOAD_DIR을 지정해 주세요.}"
: "${UPLOAD_S3_BUCKET:?UPLOAD_S3_BUCKET을 지정해 주세요.}"
: "${UPLOAD_CDN_BASE_URL:?UPLOAD_CDN_BASE_URL을 지정해 주세요.}"

DRY_RUN=false
if [[ "${1:-}" == "--dry-run" ]]; then
  DRY_RUN=true
fi

CDN_BASE_URL="${UPLOAD_CDN_BASE_URL%/}"
if [[ ! "$CDN_BASE_URL" =~ ^https://[A-Za-z0-9.-]+(/[A-Za-z0-9._/-]*)?$ ]]; then
  echo "UPLOAD_CDN_BASE_URL은 https 주소여야 합니다: $UPLOAD_CDN_BASE_URL" >&2
  exit 1
fi

SQL_FILE="rewrite-local-image-urls.sql"
copied=0
skipped=0

shopt -s nullglob
for file in "$UPLOAD_DIR"/*; do
  name="$(basename "$file")"
  # 앱이 만든 파일 이름({UUID}.확장자)만 옮긴다. 앱이 S3에서 관리하는 키 형식과 같다.
  if [[ ! -f "$file" || ! "$name" =~ ^[0-9a-f-]{36}\.(jpg|png|gif|webp)$ ]]; then
    echo "건너뜀: $name" >&2
    skipped=$((skipped + 1))
    continue
  fi
  case "${name##*.}" in
    jpg) content_type="image/jpeg" ;;
    png) content_type="image/png" ;;
    gif) content_type="image/gif" ;;
    webp) content_type="image/webp" ;;
  esac

  if [[ "$DRY_RUN" == true ]]; then
    echo "복사 예정: $name -> s3://$UPLOAD_S3_BUCKET/products/legacy/$name"
  else
    aws s3 cp "$file" "s3://$UPLOAD_S3_BUCKET/products/legacy/$name" \
      --content-type "$content_type" \
      --cache-control "public, max-age=31536000, immutable" \
      --only-show-errors
  fi
  copied=$((copied + 1))
done

# '/files/' 다음(8번째 글자)부터가 파일 이름이다. 위에서 옮긴 이름 형식만 바꾼다.
cat > "$SQL_FILE" <<SQL
-- scripts/migrate-local-uploads-to-s3.sh가 만든 파일. 이미지를 S3에 복사한 뒤 실행한다.
START TRANSACTION;
UPDATE product
SET image_url = CONCAT('$CDN_BASE_URL/products/legacy/', SUBSTRING(image_url, 8))
WHERE image_url REGEXP '^/files/[0-9a-f-]{36}[.](jpg|png|gif|webp)$';
UPDATE banner
SET image_url = CONCAT('$CDN_BASE_URL/products/legacy/', SUBSTRING(image_url, 8))
WHERE image_url REGEXP '^/files/[0-9a-f-]{36}[.](jpg|png|gif|webp)$';
COMMIT;

-- 남은 행이 있으면 형식이 다른 주소다. 직접 확인해 바꾼다.
SELECT 'product' AS source, id, image_url FROM product WHERE image_url LIKE '/files/%'
UNION ALL
SELECT 'banner', id, image_url FROM banner WHERE image_url LIKE '/files/%';
SQL

echo "복사 ${copied}개, 건너뜀 ${skipped}개. DB 주소 변경 SQL: $SQL_FILE"
