locals {
  # 앱이 요청을 받는 포트. Dockerfile의 EXPOSE와 같아야 한다.
  api_port = 8080

  # Dockerfile의 USER와 같은 UID·GID. EFS 액세스 포인트가 이 사용자로 파일을 읽고 쓴다.
  app_posix_id = 10001
}
