import boto3
import json
from app.core.config import settings

def get_sns_client():
    return boto3.client(
        'sns',
        region_name=settings.AWS_REGION,
        endpoint_url=settings.AWS_ENDPOINT_URL,
        aws_access_key_id="test",
        aws_secret_access_key="test"
    )

def publish_diagram_processed_event(diagram_id: str, extracted_data: str):
    sns = get_sns_client()
    
    message = json.dumps({
        "diagramId": diagram_id,
        "extractedData": extracted_data,
        "eventType": "DIAGRAM_PROCESSED"
    })
    
    sns.publish(
        TopicArn=settings.SNS_TOPIC_ARN,
        Message=message
    )
