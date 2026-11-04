# ---- 공통 ----

variable "name" {
  description = "리소스 이름 앞에 붙는 접두어"
  type        = string
}

variable "aws_region" {
  type = string
}

variable "api_port" {
  description = "앱이 요청을 받는 포트"
  type        = number
}

# ---- 쓰지 않아도 요금이 나오는 리소스 ----

variable "enable_alb" {
  description = "ALB를 만든다."
  type        = bool
  default     = false
}

variable "enable_service" {
  description = "ECS 태스크 정의와 서비스를 만든다. 켜려면 RDS, Redis, ALB, NAT Gateway가 있어야 한다."
  type        = bool
  default     = false
}

variable "enable_efs" {
  description = "업로드 EFS 볼륨을 태스크에 붙인다."
  type        = bool
  default     = false
}

# ---- 네트워크 ----

variable "vpc_id" {
  type = string
}

variable "public_subnet_ids" {
  type = list(string)
}

variable "app_subnet_ids" {
  type = list(string)
}

variable "alb_security_group_id" {
  type = string
}

variable "app_security_group_id" {
  type = string
}

variable "trusted_proxy_cidrs" {
  description = "앱이 X-Forwarded-For를 믿을 프록시(ALB가 있는 공용 서브넷) CIDR"
  type        = list(string)
}

# ---- 앱 설정 ----

variable "certificate_arn" {
  type = string
}

variable "image_tag" {
  type = string
}

variable "desired_count" {
  type = number
}

variable "app_environment" {
  type    = map(string)
  default = {}
}

variable "application_secret_arns" {
  type      = map(string)
  sensitive = true
}

variable "database_app_secret_arn" {
  type = string
}

variable "cors_allowed_origins" {
  type = string
}

variable "refresh_cookie_same_site" {
  type = string
}

variable "upload_storage" {
  type = string
}

# ---- 다른 모듈이 만든 값 ----

variable "db_name" {
  type = string
}

variable "db_address" {
  description = "RDS 접속 주소. 서비스를 켤 때 필요하다."
  type        = string
  default     = null
}

variable "redis_endpoint" {
  description = "Redis 접속 주소. 서비스를 켤 때 필요하다."
  type        = string
  default     = null
}

variable "redis_secret_arns" {
  description = "Redis AUTH 토큰 시크릿 ARN. 서비스를 켤 때 하나가 있어야 한다."
  type        = list(string)
  default     = []
}

variable "uploads_bucket" {
  type = string
}

variable "uploads_cdn_url" {
  type = string
}

variable "efs_file_system_id" {
  type    = string
  default = null
}

variable "efs_file_system_arn" {
  type    = string
  default = null
}

variable "efs_access_point_id" {
  type    = string
  default = null
}

variable "efs_access_point_arn" {
  type    = string
  default = null
}
