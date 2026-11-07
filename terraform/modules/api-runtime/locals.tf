locals {
  # application_secret_arns를 컨테이너 정의의 secrets 형식으로 바꾼 값
  app_secrets = [
    for name, arn in var.application_secret_arns : {
      name      = name
      valueFrom = arn
    }
  ]

  # 태스크 정의의 컨테이너 이름. ECS 서비스가 ALB에 연결할 컨테이너를 이 이름으로 찾는다.
  api_container_name = "api"

  # 컨테이너 안에서 업로드 볼륨이 붙는 경로. 앱의 UPLOAD_DIR도 이 값을 쓴다.
  uploads_mount_path = "/app/uploads"

  # RDS 접속 주소(#133). TLS로 접속하고 인증서와 호스트 이름을 이미지에 넣은 RDS CA 신뢰 저장소(Dockerfile)로만 검증한다.
  # 저장소에는 공개 인증서만 있으므로 비밀번호는 비밀이 아니다. 기본 JVM 신뢰 저장소로는 넘어가지 않는다.
  db_url = join("", [
    "jdbc:mysql://${var.db_address}:3306/${var.db_name}?sslMode=VERIFY_IDENTITY",
    "&trustCertificateKeyStoreUrl=file:/app/rds-truststore.p12",
    "&trustCertificateKeyStoreType=PKCS12",
    "&trustCertificateKeyStorePassword=rds-truststore",
    "&fallbackToSystemTrustStore=false",
  ])

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
