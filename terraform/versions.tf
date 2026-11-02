# Terraform·프로바이더 버전과 상태 저장 위치.
# 백엔드 값은 저장소에 넣지 않고 초기화할 때 넘긴다: terraform init -backend-config=backend.hcl
terraform {
  required_version = ">= 1.10.0"

  backend "s3" {}

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}
