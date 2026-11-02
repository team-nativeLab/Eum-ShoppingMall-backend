# upload_storage = "s3"일 때 업로드 이미지(상품·배너)를 저장하는 비공개 S3 버킷. CloudFront(OAC)로만 내려준다.
# 앱은 전용 IAM 사용자로 접속한다. 비밀 값이 상태 파일에 남지 않도록 액세스 키는 Terraform 밖에서 만들어
# Secrets Manager에 넣고, 그 ARN을 application_secret_arns의 UPLOAD_S3_ACCESS_KEY·UPLOAD_S3_SECRET_KEY로 넘긴다.

resource "aws_s3_bucket" "uploads" {
  bucket_prefix = "${var.name}-uploads-"
}

resource "aws_s3_bucket_public_access_block" "uploads" {
  bucket                  = aws_s3_bucket.uploads.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "uploads" {
  bucket = aws_s3_bucket.uploads.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "uploads" {
  bucket = aws_s3_bucket.uploads.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# 미사용 이미지 정리 작업이 파일을 지운다. 버전 관리를 켜 두어 지운 파일을 30일 동안 되살릴 수 있다.
resource "aws_s3_bucket_versioning" "uploads" {
  bucket = aws_s3_bucket.uploads.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "uploads" {
  bucket = aws_s3_bucket.uploads.id

  rule {
    id     = "expire-deleted-images"
    status = "Enabled"
    filter {}

    noncurrent_version_expiration {
      noncurrent_days = 30
    }
    expiration {
      expired_object_delete_marker = true
    }
    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }

  depends_on = [aws_s3_bucket_versioning.uploads]
}

# 관리자 화면의 브라우저가 Presigned URL로 직접 PUT 업로드한다.
resource "aws_s3_bucket_cors_configuration" "uploads" {
  bucket = aws_s3_bucket.uploads.id
  cors_rule {
    allowed_methods = ["PUT"]
    allowed_origins = [for origin in split(",", var.cors_allowed_origins) : trimspace(origin)]
    allowed_headers = ["*"]
    expose_headers  = ["ETag"]
    max_age_seconds = 3000
  }
}

resource "aws_cloudfront_origin_access_control" "uploads" {
  name                              = "${var.name}-uploads"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

data "aws_cloudfront_cache_policy" "caching_optimized" {
  name = "Managed-CachingOptimized"
}

# X-Content-Type-Options: nosniff가 들어 있어 브라우저가 업로드 파일을 HTML이나 스크립트로 해석하지 않는다.
data "aws_cloudfront_response_headers_policy" "security_headers" {
  name = "Managed-SecurityHeadersPolicy"
}

resource "aws_cloudfront_distribution" "uploads" {
  enabled      = true
  comment      = "${var.name} uploaded images"
  http_version = "http2and3"
  # 북미·유럽·아시아(한국 포함) 엣지를 쓴다.
  price_class = "PriceClass_200"

  origin {
    origin_id                = "uploads"
    domain_name              = aws_s3_bucket.uploads.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.uploads.id
  }

  default_cache_behavior {
    target_origin_id           = "uploads"
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD"]
    cached_methods             = ["GET", "HEAD"]
    compress                   = true
    cache_policy_id            = data.aws_cloudfront_cache_policy.caching_optimized.id
    response_headers_policy_id = data.aws_cloudfront_response_headers_policy.security_headers.id
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }
}

resource "aws_s3_bucket_policy" "uploads" {
  bucket = aws_s3_bucket.uploads.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "CloudFrontReadImages"
      Effect    = "Allow"
      Principal = { Service = "cloudfront.amazonaws.com" }
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.uploads.arn}/products/*"
      Condition = {
        StringEquals = { "AWS:SourceArn" = aws_cloudfront_distribution.uploads.arn }
      }
    }]
  })
  depends_on = [aws_s3_bucket_public_access_block.uploads]
}

resource "aws_iam_user" "uploads" {
  name = "${var.name}-uploads"
}

# 이 버킷의 이미지 경로(products/)만 허용한다. 키가 유출돼도 다른 곳은 건드릴 수 없다.
resource "aws_iam_user_policy" "uploads" {
  name = "${var.name}-uploads-s3"
  user = aws_iam_user.uploads.name
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"]
        Resource = "${aws_s3_bucket.uploads.arn}/products/*"
      },
      {
        # 정리 작업이 목록을 읽어야 하고, 이 권한이 있어야 없는 파일의 HeadObject가 403이 아닌 404를 돌려준다.
        Effect   = "Allow"
        Action   = "s3:ListBucket"
        Resource = aws_s3_bucket.uploads.arn
      }
    ]
  })
}
