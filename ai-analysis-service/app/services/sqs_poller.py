import asyncio
import boto3
import json
import logging
from app.core.config import settings
from app.services.llm_service import analyze_architecture
from app.services.sns_service import publish_analysis_completed_event

logger = logging.getLogger(__name__)

def get_sqs_client():
    return boto3.client(
        'sqs',
        region_name=settings.AWS_REGION,
        endpoint_url=settings.AWS_ENDPOINT_URL,
        aws_access_key_id="test",
        aws_secret_access_key="test"
    )

async def start_polling():
    sqs = get_sqs_client()
    queue_url = settings.SQS_QUEUE_URL

    logger.info(f"Starting to poll SQS queue: {queue_url}")

    while True:
        try:
            response = sqs.receive_message(
                QueueUrl=queue_url,
                MaxNumberOfMessages=1,
                WaitTimeSeconds=10
            )

            messages = response.get('Messages', [])
            for message in messages:
                receipt_handle = message['ReceiptHandle']
                body = json.loads(message['Body'])
                
                if 'Message' in body:
                    data = json.loads(body['Message'])
                else:
                    data = body

                diagram_id = data.get('diagramId')
                extracted_data = data.get('extractedData')

                if diagram_id and extracted_data:
                    logger.info(f"Analyzing diagram: {diagram_id}")
                    
                    # 1. Analyze Architecture
                    analysis_result = analyze_architecture(extracted_data)
                    
                    # 2. Publish Completed Event
                    publish_analysis_completed_event(diagram_id, analysis_result)
                    
                    # Delete message from queue
                    sqs.delete_message(
                        QueueUrl=queue_url,
                        ReceiptHandle=receipt_handle
                    )
        except Exception as e:
            logger.error(f"Error polling SQS: {str(e)}")
        
        await asyncio.sleep(1)
