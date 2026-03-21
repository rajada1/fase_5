# S3 Bucket for diagrams
resource "aws_s3_bucket" "diagrams" {
  bucket = "architecture-diagrams-${var.environment}"
}

# SNS Topics
resource "aws_sns_topic" "file_uploaded" {
  name = "file-uploaded-topic-${var.environment}"
}

resource "aws_sns_topic" "diagram_processed" {
  name = "diagram-processed-topic-${var.environment}"
}

resource "aws_sns_topic" "analysis_completed" {
  name = "analysis-completed-topic-${var.environment}"
}

# SQS Dead Letter Queues
resource "aws_sqs_queue" "file_uploaded_dlq" {
  name = "file-uploaded-dlq-${var.environment}"
}

resource "aws_sqs_queue" "diagram_processed_dlq" {
  name = "diagram-processed-dlq-${var.environment}"
}

resource "aws_sqs_queue" "analysis_completed_dlq" {
  name = "analysis-completed-dlq-${var.environment}"
}

# SQS Queues
resource "aws_sqs_queue" "file_uploaded_queue" {
  name = "file-uploaded-queue-${var.environment}"
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.file_uploaded_dlq.arn
    maxReceiveCount     = 3
  })
}

resource "aws_sqs_queue" "diagram_processed_queue" {
  name = "diagram-processed-queue-${var.environment}"
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.diagram_processed_dlq.arn
    maxReceiveCount     = 3
  })
}

resource "aws_sqs_queue" "analysis_completed_queue" {
  name = "analysis-completed-queue-${var.environment}"
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.analysis_completed_dlq.arn
    maxReceiveCount     = 3
  })
}

# Subscriptions (SNS -> SQS)
resource "aws_sns_topic_subscription" "file_uploaded_sub" {
  topic_arn = aws_sns_topic.file_uploaded.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.file_uploaded_queue.arn
}

resource "aws_sns_topic_subscription" "diagram_processed_sub" {
  topic_arn = aws_sns_topic.diagram_processed.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.diagram_processed_queue.arn
}

resource "aws_sns_topic_subscription" "analysis_completed_sub" {
  topic_arn = aws_sns_topic.analysis_completed.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.analysis_completed_queue.arn
}

# RDS Subnet Group and Instance
resource "aws_db_instance" "postgres" {
  identifier           = "architecture-db-${var.environment}"
  allocated_storage    = 20
  engine               = "postgres"
  engine_version       = "15.4"
  instance_class       = "db.t3.micro"
  username             = "postgres"
  password             = "postgres" # use secrets manager in prod
  db_subnet_group_name = aws_db_subnet_group.rds_subnet_group.name
  vpc_security_group_ids = [aws_security_group.rds_sg.id]
  skip_final_snapshot  = false
  final_snapshot_identifier = "architecture-db-final-${var.environment}"
  backup_retention_period = 7
  multi_az             = true
  publicly_accessible  = false
}

# ECR Repositories
resource "aws_ecr_repository" "api_gateway" {
  name = "api-gateway-${var.environment}"
}

resource "aws_ecr_repository" "upload_service" {
  name = "upload-service-${var.environment}"
}

resource "aws_ecr_repository" "processing_service" {
  name = "processing-service-${var.environment}"
}

resource "aws_ecr_repository" "ai_analysis_service" {
  name = "ai-analysis-service-${var.environment}"
}

resource "aws_ecr_repository" "report_service" {
  name = "report-service-${var.environment}"
}

resource "aws_ecr_repository" "status_service" {
  name = "status-service-${var.environment}"
}
