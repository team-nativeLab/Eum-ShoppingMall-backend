locals {
  # 서브넷을 나눠 둘 AZ 2개
  azs = slice(data.aws_availability_zones.available.names, 0, 2)

  # application_secret_arns를 컨테이너 정의의 secrets 형식으로 바꾼 값
  app_secrets = [
    for name, arn in var.application_secret_arns : {
      name      = name
      valueFrom = arn
    }
  ]

  # 앱이 요청을 받는 포트. Dockerfile의 EXPOSE와 같아야 한다.
  api_port = 8080

  # 태스크 정의의 컨테이너 이름. ECS 서비스가 ALB에 연결할 컨테이너를 이 이름으로 찾는다.
  api_container_name = "api"

  # Dockerfile의 USER와 같은 UID·GID. EFS 액세스 포인트가 이 사용자로 파일을 읽고 쓴다.
  app_posix_id = 10001

  # 컨테이너 안에서 업로드 볼륨이 붙는 경로. 앱의 UPLOAD_DIR도 이 값을 쓴다.
  uploads_mount_path = "/app/uploads"

  # ECS 실행 역할과 태스크 역할이 함께 쓰는 신뢰 정책
  ecs_tasks_assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}
