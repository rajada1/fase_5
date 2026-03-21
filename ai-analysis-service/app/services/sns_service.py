import boto3
import json
from app.core.config import settings
from app.models.analysis import AnalysisResult

def get_sns_client():
    return boto3.client(
        'sns',
        region_name=settings.AWS_REGION,
        endpoint_url=settings.AWS_ENDPOINT_URL,
        aws_access_key_id="test",
        aws_secret_access_key="test"
    )

def publish_analysis_completed_event(diagram_id: str, analysis_result: AnalysisResult):
    sns = get_sns_client()
    
    message = json.dumps({
        "diagramId": diagram_id,
        "analysis": analysis_result.dict(),
        "eventType": "ANALYSIS_COMPLETED"
    })
    
    sns.publish(
        TopicArn=settings.SNS_TOPIC_ARN,
        Message=message
    )
