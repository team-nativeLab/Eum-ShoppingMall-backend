# Uploaded product and banner images: a private S3 bucket served only through CloudFront (OAC).
# The app signs in as a dedicated IAM user. Create its access key outside Terraform (so the secret never
# lands in state), store it in Secrets Manager, and pass the ARNs as UPLOAD_S3_ACCESS_KEY/UPLOAD_S3_SECRET_KEY.

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

# The unused-image cleanup job deletes objects; versioning keeps them recoverable for 30 days.
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

# Admin browsers upload directly with presigned PUT URLs.
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

# Includes X-Content-Type-Options: nosniff, so browsers never treat an uploaded object as HTML or script.
data "aws_cloudfront_response_headers_policy" "security_headers" {
  name = "Managed-SecurityHeadersPolicy"
}

resource "aws_cloudfront_distribution" "uploads" {
  enabled      = true
  comment      = "${var.name} uploaded images"
  http_version = "http2and3"
  # Edge locations in North America, Europe, and Asia (including Korea).
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

# Only the image prefix of this bucket; a leaked key cannot touch anything else.
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
        # Listing is needed by the cleanup job, and lets HeadObject return 404 instead of 403 for missing uploads.
        Effect   = "Allow"
        Action   = "s3:ListBucket"
        Resource = aws_s3_bucket.uploads.arn
      }
    ]
  })
}
