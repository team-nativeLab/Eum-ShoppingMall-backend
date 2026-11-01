provider "aws" {
  region = var.aws_region

  # 이 구성이 만드는 모든 리소스에 붙는 태그
  default_tags {
    tags = {
      Project   = var.name
      ManagedBy = "Terraform"
    }
  }
}
