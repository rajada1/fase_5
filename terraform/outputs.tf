# outputs.tf - Useful outputs for CI/CD and service configuration

output "alb_dns_name" {
  description = "DNS name of the Application Load Balancer"
  value       = aws_lb.main.dns_name
}

output "ecs_cluster_name" {
  description = "ECS Cluster name"
  value       = aws_ecs_cluster.main.name
}

output "rds_endpoint" {
  description = "RDS PostgreSQL endpoint"
  value       = aws_db_instance.postgres.address
}

output "s3_bucket_name" {
  description = "S3 bucket for diagrams"
  value       = aws_s3_bucket.diagrams.bucket
}

output "cognito_user_pool_id" {
  description = "Cognito User Pool ID"
  value       = aws_cognito_user_pool.pool.id
}

output "cognito_client_id" {
  description = "Cognito App Client ID"
  value       = aws_cognito_user_pool_client.client.id
}

output "cognito_domain" {
  description = "Cognito domain for authentication"
  value       = "https://${aws_cognito_user_pool_domain.main.domain}.auth.${var.aws_region}.amazoncognito.com"
}

# SNS Topic ARNs
output "sns_file_uploaded_arn" {
  value = aws_sns_topic.file_uploaded.arn
}

output "sns_diagram_processed_arn" {
  value = aws_sns_topic.diagram_processed.arn
}

output "sns_analysis_completed_arn" {
  value = aws_sns_topic.analysis_completed.arn
}

# SQS Queue URLs
output "sqs_file_uploaded_queue_url" {
  value = aws_sqs_queue.file_uploaded_queue.id
}

output "sqs_diagram_processed_queue_url" {
  value = aws_sqs_queue.diagram_processed_queue.id
}

output "sqs_analysis_completed_queue_url" {
  value = aws_sqs_queue.analysis_completed_queue.id
}

output "sqs_status_file_uploaded_queue_url" {
  value = aws_sqs_queue.status_file_uploaded_queue.id
}

output "sqs_status_diagram_processed_queue_url" {
  value = aws_sqs_queue.status_diagram_processed_queue.id
}

output "sqs_status_analysis_completed_queue_url" {
  value = aws_sqs_queue.status_analysis_completed_queue.id
}

# ECR Repository URLs
output "ecr_api_gateway_url" {
  value = aws_ecr_repository.api_gateway.repository_url
}

output "ecr_upload_service_url" {
  value = aws_ecr_repository.upload_service.repository_url
}

output "ecr_processing_service_url" {
  value = aws_ecr_repository.processing_service.repository_url
}

output "ecr_ai_analysis_service_url" {
  value = aws_ecr_repository.ai_analysis_service.repository_url
}

output "ecr_report_service_url" {
  value = aws_ecr_repository.report_service.repository_url
}

output "ecr_status_service_url" {
  value = aws_ecr_repository.status_service.repository_url
}
