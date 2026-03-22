Write-Host "Initializing Localstack AWS Resources..."

$env:AWS_ACCESS_KEY_ID="test"
$env:AWS_SECRET_ACCESS_KEY="test"
$env:AWS_DEFAULT_REGION="us-east-1"

function awslocal {
    aws --endpoint-url=http://localhost:4566 @args
}

Write-Host "Creating S3 bucket..."
awslocal s3 mb s3://architecture-diagrams-dev

Write-Host "Creating Dead Letter Queues (DLQs)..."
awslocal sqs create-queue --queue-name file-uploaded-dlq-dev
awslocal sqs create-queue --queue-name diagram-processed-dlq-dev
awslocal sqs create-queue --queue-name analysis-completed-dlq-dev

Write-Host "Creating primary SQS queues with Redrive Policy..."
awslocal sqs create-queue --queue-name file-uploaded-queue-dev --attributes '{"RedrivePolicy":"{\"deadLetterTargetArn\":\"arn:aws:sqs:us-east-1:000000000000:file-uploaded-dlq-dev\",\"maxReceiveCount\":\"3\"}"}'
awslocal sqs create-queue --queue-name diagram-processed-queue-dev --attributes '{"RedrivePolicy":"{\"deadLetterTargetArn\":\"arn:aws:sqs:us-east-1:000000000000:diagram-processed-dlq-dev\",\"maxReceiveCount\":\"3\"}"}'
awslocal sqs create-queue --queue-name analysis-completed-queue-dev --attributes '{"RedrivePolicy":"{\"deadLetterTargetArn\":\"arn:aws:sqs:us-east-1:000000000000:analysis-completed-dlq-dev\",\"maxReceiveCount\":\"3\"}"}'

Write-Host "Creating dedicated SQS queues for status-service fanout consumption..."
awslocal sqs create-queue --queue-name status-file-uploaded-queue-dev
awslocal sqs create-queue --queue-name status-diagram-processed-queue-dev
awslocal sqs create-queue --queue-name status-analysis-completed-queue-dev

Write-Host "Creating SNS topics..."
$TOPIC_UPLOAD_ARN = (awslocal sns create-topic --name file-uploaded-topic-dev --output text --query 'TopicArn')
$TOPIC_PROCESSED_ARN = (awslocal sns create-topic --name diagram-processed-topic-dev --output text --query 'TopicArn')
$TOPIC_ANALYSIS_ARN = (awslocal sns create-topic --name analysis-completed-topic-dev --output text --query 'TopicArn')

Write-Host "Subscribing SQS queues to SNS topics..."
awslocal sns subscribe --topic-arn $TOPIC_UPLOAD_ARN --protocol sqs --notification-endpoint "arn:aws:sqs:us-east-1:000000000000:file-uploaded-queue-dev"
awslocal sns subscribe --topic-arn $TOPIC_PROCESSED_ARN --protocol sqs --notification-endpoint "arn:aws:sqs:us-east-1:000000000000:diagram-processed-queue-dev"
awslocal sns subscribe --topic-arn $TOPIC_ANALYSIS_ARN --protocol sqs --notification-endpoint "arn:aws:sqs:us-east-1:000000000000:analysis-completed-queue-dev"

Write-Host "Subscribing status-service queues to SNS topics..."
awslocal sns subscribe --topic-arn $TOPIC_UPLOAD_ARN --protocol sqs --notification-endpoint "arn:aws:sqs:us-east-1:000000000000:status-file-uploaded-queue-dev"
awslocal sns subscribe --topic-arn $TOPIC_PROCESSED_ARN --protocol sqs --notification-endpoint "arn:aws:sqs:us-east-1:000000000000:status-diagram-processed-queue-dev"
awslocal sns subscribe --topic-arn $TOPIC_ANALYSIS_ARN --protocol sqs --notification-endpoint "arn:aws:sqs:us-east-1:000000000000:status-analysis-completed-queue-dev"

Write-Host "Localstack initialization completed."
