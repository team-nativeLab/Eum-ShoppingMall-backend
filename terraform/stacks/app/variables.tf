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
  description = "프론트엔드 HTTPS origin(고객 사이트와 관리자 화면). 정확한 값을 쉼표로 구분해 적는다."
  type        = string
}

variable "cors_admin_origins" {
  description = "cors_allowed_origins 중 관리자 화면 origin. 관리자 Refresh 쿠키(재발급·로그아웃)는 이 origin에서 온 요청만 받는다. 비우면 관리자는 Access 토큰이 끝날 때마다(15분) 다시 로그인해야 한다."
  type        = string
  default     = ""

  validation {
    condition = alltrue([
      for origin in compact([for value in split(",", var.cors_admin_origins) : trimspace(value)]) :
      contains([for value in split(",", var.cors_allowed_origins) : trimspace(value)], origin)
    ])
    error_message = "cors_admin_origins의 주소는 cors_allowed_origins에도 넣어야 관리자 화면이 API를 부를 수 있습니다."
  }
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

# ---- 쓰지 않아도 요금이 나오는 리소스 ----
# 모두 기본값이 false다. 켜는 순서와 구성별 비용은 docs/deployment-aws.md의 "비용과 구성 단계"를 참고한다.

variable "enable_data_layer" {
  description = "RDS와 Redis를 만든다. 스키마를 준비할 때부터 켠다."
  type        = bool
  default     = false
}

variable "enable_nat_gateway" {
  description = "NAT Gateway를 만든다. ECS 태스크가 ECR, Secrets Manager, 외부 API에 닿으려면 필요하다."
  type        = bool
  default     = false
}

variable "enable_alb" {
  description = "ALB를 만든다. 지웠다가 다시 만들면 DNS 이름이 바뀌어 API 도메인을 다시 연결해야 한다."
  type        = bool
  default     = false
}

variable "enable_efs" {
  description = "업로드 이미지용 EFS를 만든다. upload_storage가 local이면 ECS 서비스에 필요하다. 끄면 저장된 파일도 지워진다."
  type        = bool
  default     = false
}

variable "enable_ecs_service" {
  description = "ECS 태스크 정의와 서비스를 만든다. 제한된 DB 계정과 스키마가 준비될 때까지 false로 둔다."
  type        = bool
  default     = false
  validation {
    condition     = !var.enable_ecs_service || (var.enable_data_layer && var.enable_nat_gateway && var.enable_alb)
    error_message = "enable_ecs_service를 켜려면 enable_data_layer, enable_nat_gateway, enable_alb도 켜야 합니다."
  }
  validation {
    condition     = !var.enable_ecs_service || var.upload_storage == "s3" || var.enable_efs
    error_message = "upload_storage가 local이면 enable_ecs_service를 켤 때 enable_efs도 켜야 합니다."
  }
}

# ---- 비용 경보 ----

variable "budget_limit_usd" {
  description = "월 예산(USD). AWS 계정 전체 비용과 비교한다. 적용 전에 팀에서 확정한다."
  type        = number
  validation {
    condition     = var.budget_limit_usd > 0
    error_message = "budget_limit_usd에 팀에서 확정한 월 예산을 넣어 주세요. 0보다 커야 합니다."
  }
}

variable "budget_alert_emails" {
  description = "예산 알림을 받을 이메일(1~10개). 적용 전에 팀에서 확정한다."
  type        = list(string)
  validation {
    condition     = length(var.budget_alert_emails) >= 1 && length(var.budget_alert_emails) <= 10
    error_message = "budget_alert_emails는 1개 이상 10개 이하여야 합니다. AWS가 알림 하나에 이메일 10개까지만 받습니다."
  }
  validation {
    condition = alltrue([
      for email in var.budget_alert_emails :
      can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", email)) && !endswith(email, "@example.com")
    ])
    error_message = "budget_alert_emails에 팀에서 확정한 실제 이메일을 넣어 주세요. example.com 주소로는 알림을 받을 수 없습니다."
  }
}

variable "budget_alert_thresholds" {
  description = "실제 비용이 월 예산의 몇 %를 넘으면 알릴지. 예측 비용 100% 알림은 따로 보낸다."
  type        = list(number)
  default     = [50, 80, 100]
  validation {
    condition = (
      length(var.budget_alert_thresholds) >= 1 &&
      length(var.budget_alert_thresholds) <= 4 &&
      length(distinct(var.budget_alert_thresholds)) == length(var.budget_alert_thresholds)
    )
    error_message = "budget_alert_thresholds는 서로 다른 값 1~4개여야 합니다. AWS가 예산 하나에 알림 5개까지만 받고, 그중 하나는 예측 비용 알림입니다."
  }
  validation {
    condition     = alltrue([for threshold in var.budget_alert_thresholds : threshold > 0])
    error_message = "budget_alert_thresholds의 값은 0보다 커야 합니다."
  }
}

# ---- ECS 배포 ----

variable "image_tag" {
  description = "실행할 ECR 이미지 태그. 덮어쓸 수 없으므로 Git 커밋 SHA를 권장한다."
  type        = string
}

variable "app_environment" {
  description = "앱에 넘길 추가 환경변수(ALERT_EMAILS, MAIL_HOST, LOG_FORMAT 등). 비밀 값은 application_secret_arns로 넘긴다."
  type        = map(string)
  default     = {}
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
