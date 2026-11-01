# ---- 공통 ----

variable "name" {
  description = "리소스 이름 앞에 붙는 짧은 접두어. 영문 소문자·숫자·하이픈만 쓴다."
  type        = string
  default     = "eum-shopping"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{2,20}$", var.name))
    error_message = "name은 영문 소문자로 시작하고 영문 소문자·숫자·하이픈으로 된 3~21자여야 합니다."
  }
}

variable "aws_region" {
  description = "리소스를 만들 AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

# ---- 네트워크 ----

variable "vpc_cidr" {
  description = "VPC CIDR"
  type        = string
  default     = "10.20.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "공용 서브넷 CIDR(ALB, NAT Gateway). AZ마다 하나씩 2개."
  type        = list(string)
  default     = ["10.20.0.0/24", "10.20.1.0/24"]
  validation {
    condition     = length(var.public_subnet_cidrs) == 2
    error_message = "공용 서브넷 CIDR은 AZ마다 하나씩 정확히 2개여야 합니다."
  }
}

variable "app_subnet_cidrs" {
  description = "앱 서브넷 CIDR(ECS 태스크, EFS). AZ마다 하나씩 2개."
  type        = list(string)
  default     = ["10.20.10.0/24", "10.20.11.0/24"]
  validation {
    condition     = length(var.app_subnet_cidrs) == 2
    error_message = "앱 서브넷 CIDR은 AZ마다 하나씩 정확히 2개여야 합니다."
  }
}

variable "data_subnet_cidrs" {
  description = "데이터 서브넷 CIDR(RDS, Redis). AZ마다 하나씩 2개."
  type        = list(string)
  default     = ["10.20.20.0/24", "10.20.21.0/24"]
  validation {
    condition     = length(var.data_subnet_cidrs) == 2
    error_message = "데이터 서브넷 CIDR은 AZ마다 하나씩 정확히 2개여야 합니다."
  }
}

# ---- 도메인·프론트엔드 ----

variable "certificate_arn" {
  description = "HTTPS 리스너에 쓸 이 리전의 ACM 인증서 ARN"
  type        = string
}

variable "api_domain" {
  description = "certificate_arn에 포함된 API 도메인. 예: api.example.com"
  type        = string
}

variable "cors_allowed_origins" {
  description = "프론트엔드 HTTPS origin. 정확한 값을 쉼표로 구분해 적는다."
  type        = string
}

variable "refresh_cookie_same_site" {
  description = "Refresh 토큰 쿠키의 SameSite. 웹 프론트가 API와 같은 등록 도메인을 쓰면(shop.example.com과 api.example.com) Strict를 유지한다. None을 쓰기 전에 docs/deployment-proxy.md를 확인한다."
  type        = string
  default     = "Strict"

  validation {
    condition     = contains(["Strict", "Lax", "None"], var.refresh_cookie_same_site)
    error_message = "refresh_cookie_same_site는 Strict, Lax, None 중 하나여야 합니다."
  }
}

# ---- ECS 배포 ----

variable "image_tag" {
  description = "실행할 ECR 이미지 태그. 덮어쓸 수 없으므로 Git 커밋 SHA를 권장한다."
  type        = string
}

variable "enable_ecs_service" {
  description = "ECS 서비스 생성 여부. 제한된 DB 계정과 스키마가 준비될 때까지 false로 둔다."
  type        = bool
  default     = false
}

variable "desired_count" {
  description = "실행할 ECS 태스크 수"
  type        = number
  default     = 1
  validation {
    condition     = var.desired_count >= 1
    error_message = "ECS 서비스를 켜려면 desired_count는 1 이상이어야 합니다."
  }
}

# ---- 앱 시크릿 ----

variable "application_secret_arns" {
  description = "앱 환경변수 이름과 Secrets Manager 시크릿 ARN의 맵. JWT_SECRET은 필수다."
  type        = map(string)
  sensitive   = true

  validation {
    condition     = contains(keys(var.application_secret_arns), "JWT_SECRET")
    error_message = "application_secret_arns에 JWT_SECRET이 있어야 합니다. 시크릿 값은 tfvars가 아니라 Secrets Manager에 넣습니다."
  }
}

variable "database_app_secret_arn" {
  description = "앱 전용 최소 권한 DB 계정의 username·password 키를 담은 Secrets Manager JSON 시크릿 ARN"
  type        = string
}

# ---- 데이터베이스 ----

variable "db_name" {
  description = "RDS에 만들 데이터베이스 이름"
  type        = string
  default     = "shopping"
}

variable "db_username" {
  description = "RDS 마스터 계정 이름. 앱은 이 계정으로 접속하지 않는다."
  type        = string
  default     = "eum_admin"
}

variable "db_instance_class" {
  description = "RDS 인스턴스 클래스"
  type        = string
  default     = "db.t4g.micro"
}

variable "db_multi_az" {
  description = "RDS Multi-AZ 사용 여부. 운영 가용성이 필요하면 켠다."
  type        = bool
  default     = false
}

# ---- Redis ----

variable "redis_node_type" {
  description = "ElastiCache 노드 타입"
  type        = string
  default     = "cache.t4g.micro"
}

variable "redis_auth_token" {
  description = "Redis AUTH 토큰(허용 문자 16~128자). 안전한 시크릿 저장소에서 TF_VAR_redis_auth_token으로 넘긴다. Terraform 상태에 이 값이 저장되므로 상태를 암호화해야 한다."
  type        = string
  sensitive   = true
  validation {
    condition     = can(regex("^[A-Za-z0-9!&#$^<>-]{16,128}$", var.redis_auth_token))
    error_message = "Redis AUTH 토큰은 영문·숫자와 !, &, #, $, ^, <, >, - 로 된 16~128자여야 합니다."
  }
}

# ---- 업로드 ----

variable "upload_storage" {
  description = "이미지 저장소. local은 EFS 볼륨에, s3는 CloudFront 뒤의 업로드 버킷에 저장한다. 기존 파일을 옮긴 뒤 s3로 바꾼다(docs/deployment-aws.md)."
  type        = string
  default     = "local"
  validation {
    condition     = contains(["local", "s3"], var.upload_storage)
    error_message = "upload_storage는 local 또는 s3여야 합니다."
  }
  validation {
    condition = var.upload_storage == "local" || (
      contains(keys(var.application_secret_arns), "UPLOAD_S3_ACCESS_KEY") &&
      contains(keys(var.application_secret_arns), "UPLOAD_S3_SECRET_KEY")
    )
    error_message = "upload_storage가 s3이면 application_secret_arns에 UPLOAD_S3_ACCESS_KEY와 UPLOAD_S3_SECRET_KEY가 있어야 합니다."
  }
}
