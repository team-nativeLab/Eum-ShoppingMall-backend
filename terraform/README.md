# 이음 쇼핑몰 인프라 (Terraform)

## 구조

```
terraform/
├── bootstrap/          state 저장용 S3 버킷 (최초 1회, local state)
├── modules/
│   ├── network/        VPC · 서브넷 · 라우팅 · NAT Gateway · 보안 그룹
│   ├── database/       RDS MySQL · ElastiCache Redis · Redis AUTH 시크릿
│   ├── photo-storage/  업로드 이미지 S3 · CloudFront · 업로드 IAM 사용자 · EFS
│   ├── api-runtime/    ECR · ALB · ECS · IAM 역할 · 로그 그룹
│   └── observability/  월 예산 경보
├── stacks/
│   └── app/            쇼핑몰 API 한 벌. 모듈을 조립하고 변수 검증과 테스트를 둔다
└── envs/
    └── app/            stacks/app의 환경별 변수 파일
```

모듈은 리소스를 만들기만 하고, 무엇을 켜고 어떻게 잇는지는 `stacks/app`이 정합니다. 쓰지 않아도 요금이 나오는 리소스는 모듈 변수의 기본값이 모두 꺼져 있습니다.

CloudFront용 WAF는 us-east-1에 따로 배포하는 CloudFormation 템플릿(`infra/edge-waf.yml`)입니다.

## 실행

```sh
# 최초 1회: state 버킷을 만들고, 출력된 이름을 stacks/app/backend.hcl의 bucket에 넣는다.
cd terraform/bootstrap
terraform init
terraform apply

cd ../stacks/app
cp backend.hcl.example backend.hcl
terraform init -backend-config=backend.hcl
terraform plan -var-file=../../envs/app/prod.tfvars
```

배포 순서와 구성별 비용은 `docs/deployment-aws.md`를 참고합니다.

## 검증

```sh
cd terraform
terraform fmt -check -recursive
cd stacks/app
terraform init -backend=false
terraform validate
terraform test
```

`terraform test`는 AWS 없이 mock provider로 plan만 돌립니다. 스택으로는 변수 검증을, 모듈별로는 켜고 끌 때 만들어지는 리소스를 확인합니다.

## 모듈로 나누기 전 환경

`stacks/app/moved.tf`가 예전 주소(`aws_vpc.main` 등)를 모듈 안 주소로 옮겨, 이미 apply한 환경에서도 리소스를 다시 만들지 않습니다. 모든 환경에 한 번씩 apply한 뒤에는 이 파일을 지워도 됩니다.
