variable "name" {
  description = "Short resource prefix. Use lowercase letters, digits, and hyphens."
  type        = string
  default     = "eum-shopping"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{2,20}$", var.name))
    error_message = "name must be 3-21 lowercase letters, digits, or hyphens and start with a letter."
  }
}

variable "aws_region" {
  type    = string
  default = "ap-northeast-2"
}

variable "vpc_cidr" {
  type    = string
  default = "10.20.0.0/16"
}

variable "public_subnet_cidrs" {
  type    = list(string)
  default = ["10.20.0.0/24", "10.20.1.0/24"]
  validation {
    condition     = length(var.public_subnet_cidrs) == 2
    error_message = "Exactly two public subnet CIDRs are required, one per Availability Zone."
  }
}

variable "app_subnet_cidrs" {
  type    = list(string)
  default = ["10.20.10.0/24", "10.20.11.0/24"]
  validation {
    condition     = length(var.app_subnet_cidrs) == 2
    error_message = "Exactly two app subnet CIDRs are required, one per Availability Zone."
  }
}

variable "data_subnet_cidrs" {
  type    = list(string)
  default = ["10.20.20.0/24", "10.20.21.0/24"]
  validation {
    condition     = length(var.data_subnet_cidrs) == 2
    error_message = "Exactly two data subnet CIDRs are required, one per Availability Zone."
  }
}

variable "certificate_arn" {
  description = "ACM certificate ARN in this region for the HTTPS listener."
  type        = string
}

variable "api_domain" {
  description = "DNS name covered by certificate_arn, for example api.example.com."
  type        = string
}

variable "image_tag" {
  description = "Immutable ECR image tag to run, preferably the Git commit SHA."
  type        = string
}

variable "cors_allowed_origins" {
  description = "Comma-separated exact HTTPS frontend origins."
  type        = string
}

variable "application_secret_arns" {
  description = "Map of application environment variable names to Secrets Manager secret ARNs; must include JWT_SECRET."
  type        = map(string)
  sensitive   = true

  validation {
    condition     = contains(keys(var.application_secret_arns), "JWT_SECRET")
    error_message = "application_secret_arns must include JWT_SECRET. Store secret values in Secrets Manager, not in tfvars."
  }
}

variable "database_app_secret_arn" {
  description = "Secrets Manager JSON secret ARN with least-privilege app database username and password keys."
  type        = string
}

variable "db_name" {
  type    = string
  default = "shopping"
}

variable "db_username" {
  type    = string
  default = "eum_admin"
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "db_multi_az" {
  description = "Enable Multi-AZ for production availability."
  type        = bool
  default     = false
}

variable "redis_node_type" {
  type    = string
  default = "cache.t4g.micro"
}

variable "desired_count" {
  type    = number
  default = 1
  validation {
    condition     = var.desired_count >= 1
    error_message = "desired_count must be at least one when the ECS service is enabled."
  }
}

variable "upload_storage" {
  description = "Image storage: local keeps files on the EFS volume, s3 uses the uploads bucket behind CloudFront. Switch to s3 after copying existing files (docs/deployment-aws.md)."
  type        = string
  default     = "local"
  validation {
    condition     = contains(["local", "s3"], var.upload_storage)
    error_message = "upload_storage must be local or s3."
  }
  validation {
    condition = var.upload_storage == "local" || (
      contains(keys(var.application_secret_arns), "UPLOAD_S3_ACCESS_KEY") &&
      contains(keys(var.application_secret_arns), "UPLOAD_S3_SECRET_KEY")
    )
    error_message = "upload_storage = s3 needs UPLOAD_S3_ACCESS_KEY and UPLOAD_S3_SECRET_KEY in application_secret_arns."
  }
}

variable "enable_ecs_service" {
  description = "Keep false until the restricted DB user and schema are ready."
  type        = bool
  default     = false
}

variable "redis_auth_token" {
  description = "Redis AUTH token (16-128 allowed characters); pass via TF_VAR_redis_auth_token from a secure secret store. Terraform state contains this value and must be encrypted."
  type        = string
  sensitive   = true
  validation {
    condition     = can(regex("^[A-Za-z0-9!&#$^<>-]{16,128}$", var.redis_auth_token))
    error_message = "Redis AUTH token must be 16-128 characters from the supported set: letters, digits, !, &, #, $, ^, <, >, and -."
  }
}
