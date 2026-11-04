# 스택들이 함께 쓰는 Terraform state 저장소. 처음 한 번만 만든다.
#
# state를 둘 버킷을 만드는 스택이라 이 스택만 local state로 실행한다.
# 버킷 이름은 stacks/app/backend.hcl의 bucket에 넣는다.
# 잠금은 DynamoDB 대신 S3 잠금 파일(use_lockfile)을 써서 고정 비용이 없다.

data "aws_caller_identity" "current" {}

resource "aws_s3_bucket" "state" {
  bucket = "${var.name}-tfstate-${data.aws_caller_identity.current.account_id}"

  # 지우면 모든 스택의 state가 사라진다.
  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket = aws_s3_bucket.state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# state에는 Redis AUTH 토큰 같은 비밀 값이 들어 있으므로 HTTPS 요청만 받는다.
resource "aws_s3_bucket_policy" "state" {
  bucket = aws_s3_bucket.state.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "DenyInsecureTransport"
      Effect    = "Deny"
      Principal = "*"
      Action    = "s3:*"
      Resource  = [aws_s3_bucket.state.arn, "${aws_s3_bucket.state.arn}/*"]
      Condition = {
        Bool = { "aws:SecureTransport" = "false" }
      }
    }]
  })
  depends_on = [aws_s3_bucket_public_access_block.state]
}

# 잘못 덮어쓴 state를 되돌릴 수 있도록 이전 버전을 90일 보관한다.
resource "aws_s3_bucket_lifecycle_configuration" "state" {
  bucket = aws_s3_bucket.state.id

  rule {
    id     = "expire-noncurrent-state"
    status = "Enabled"
    filter {}

    noncurrent_version_expiration {
      noncurrent_days = 90
    }
  }

  depends_on = [aws_s3_bucket_versioning.state]
}
