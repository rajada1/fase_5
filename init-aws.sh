#!/bin/bash
echo "Initializing Localstack AWS Resources..."

# Configure AWS CLI for Localstack
export AWS_ACCESS_KEY_ID="test"
export AWS_SECRET_ACCESS_KEY="test"
export AWS_DEFAULT_REGION="us-east-1"
alias awslocal="aws --endpoint-url=http://localhost:4566"

echo "Creating S3 bucket..."
awslocal s3 mb s3://architecture-diagrams-dev || true

echo "Creating Dead Letter Queues (DLQs)..."
awslocal sqs create-queue --queue-name file-uploaded-dlq-dev || true
awslocal sqs create-queue --queue-name diagram-processed-dlq-dev || true
awslocal sqs create-queue --queue-name analysis-completed-dlq-dev || true

echo "Creating primary SQS queues with Redrive Policy..."
awslocal sqs create-queue --queue-name file-uploaded-queue-dev --attributes '{"RedrivePolicy":"{\"deadLetterTargetArn\":\"arn:aws:sqs:us-east-1:000000000000:file-uploaded-dlq-dev\",\"maxReceiveCount\":\"3\"}"}' || true
awslocal sqs create-queue --queue-name diagram-processed-queue-dev --attributes '{"RedrivePolicy":"{\"deadLetterTargetArn\":\"arn:aws:sqs:us-east-1:000000000000:diagram-processed-dlq-dev\",\"maxReceiveCount\":\"3\"}"}' || true
awslocal sqs create-queue --queue-name analysis-completed-queue-dev --attributes '{"RedrivePolicy":"{\"deadLetterTargetArn\":\"arn:aws:sqs:us-east-1:000000000000:analysis-completed-dlq-dev\",\"maxReceiveCount\":\"3\"}"}' || true

echo "Creating dedicated SQS queues for status-service fanout consumption..."
awslocal sqs create-queue --queue-name status-file-uploaded-queue-dev || true
awslocal sqs create-queue --queue-name status-diagram-processed-queue-dev || true
awslocal sqs create-queue --queue-name status-analysis-completed-queue-dev || true

echo "Creating SNS topics..."
TOPIC_UPLOAD_ARN=$(awslocal sns create-topic --name file-uploaded-topic-dev --output text --query 'TopicArn')
TOPIC_PROCESSED_ARN=$(awslocal sns create-topic --name diagram-processed-topic-dev --output text --query 'TopicArn')
TOPIC_ANALYSIS_ARN=$(awslocal sns create-topic --name analysis-completed-topic-dev --output text --query 'TopicArn')

echo "Subscribing SQS queues to SNS topics..."
awslocal sns subscribe --topic-arn $TOPIC_UPLOAD_ARN --protocol sqs --notification-endpoint "arn:aws:sqs:us-east-1:000000000000:file-uploaded-queue-dev"
awslocal sns subscribe --topic-arn $TOPIC_PROCESSED_ARN --protocol sqs --notification-endpoint "arn:aws:sqs:us-east-1:000000000000:diagram-processed-queue-dev"
awslocal sns subscribe --topic-arn $TOPIC_ANALYSIS_ARN --protocol sqs --notification-endpoint "arn:aws:sqs:us-east-1:000000000000:analysis-completed-queue-dev"

echo "Subscribing status-service queues to SNS topics..."
awslocal sns subscribe --topic-arn $TOPIC_UPLOAD_ARN --protocol sqs --notification-endpoint "arn:aws:sqs:us-east-1:000000000000:status-file-uploaded-queue-dev"
awslocal sns subscribe --topic-arn $TOPIC_PROCESSED_ARN --protocol sqs --notification-endpoint "arn:aws:sqs:us-east-1:000000000000:status-diagram-processed-queue-dev"
awslocal sns subscribe --topic-arn $TOPIC_ANALYSIS_ARN --protocol sqs --notification-endpoint "arn:aws:sqs:us-east-1:000000000000:status-analysis-completed-queue-dev"

echo "Localstack initialization completed."

