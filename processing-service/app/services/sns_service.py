import boto3
import json
from app.core.config import settings

def get_sns_client():
    client_args = {
        "region_name": settings.AWS_REGION,
    }

    if settings.AWS_ENDPOINT_URL:
        client_args["endpoint_url"] = settings.AWS_ENDPOINT_URL

    if settings.AWS_ACCESS_KEY_ID and settings.AWS_SECRET_ACCESS_KEY:
        client_args["aws_access_key_id"] = settings.AWS_ACCESS_KEY_ID
        client_args["aws_secret_access_key"] = settings.AWS_SECRET_ACCESS_KEY

    return boto3.client('sns', **client_args)

def publish_diagram_processed_event(diagram_id: str, extracted_data: str, correlation_id: str | None = None):
    sns = get_sns_client()

    payload = {
        "diagramId": diagram_id,
        "extractedData": extracted_data,
        "eventType": "DIAGRAM_PROCESSED"
    }
    if correlation_id:
        payload["correlationId"] = correlation_id

    message = json.dumps(payload)

    sns.publish(
        TopicArn=settings.SNS_TOPIC_ARN,
        Message=message
    )


def publish_processing_failed_event(diagram_id: str, reason: str, correlation_id: str | None = None):
    sns = get_sns_client()

    payload = {
        "diagramId": diagram_id,
        "reason": (reason or "processing_failed")[:500],
        "eventType": "PROCESSING_FAILED"
    }
    if correlation_id:
        payload["correlationId"] = correlation_id

    message = json.dumps(payload)

    sns.publish(
        TopicArn=settings.SNS_TOPIC_ARN,
        Message=message
    )
