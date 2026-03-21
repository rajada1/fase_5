import asyncio
import boto3
import json
import logging
import os
from app.core.config import settings
from app.services.s3_service import download_file
from app.services.ocr_service import process_image
from app.services.sns_service import publish_diagram_processed_event

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
            response = await asyncio.to_thread(
                sqs.receive_message,
                QueueUrl=queue_url,
                MaxNumberOfMessages=1,
                WaitTimeSeconds=10
            )

            messages = response.get('Messages', [])
            for message in messages:
                receipt_handle = message['ReceiptHandle']
                body = json.loads(message['Body'])
                
                # AWS SNS wraps the message inside another 'Message' field
                if 'Message' in body:
                    data = json.loads(body['Message'])
                else:
                    data = body

                diagram_id = data.get('diagramId')
                s3_key = data.get('s3Key')

                if diagram_id and s3_key:
                    logger.info(f"Processing diagram: {diagram_id}")
                    local_path = f"/tmp/{diagram_id}"
                    
                    try:
                        # 1. Download
                        await asyncio.to_thread(download_file, s3_key, local_path)
                        
                        # 2. Process
                        extracted_data = await asyncio.to_thread(process_image, local_path)
                        
                        # 3. Publish
                        await asyncio.to_thread(publish_diagram_processed_event, diagram_id, extracted_data)
                        
                        # Delete message from queue
                        await asyncio.to_thread(
                            sqs.delete_message,
                            QueueUrl=queue_url,
                            ReceiptHandle=receipt_handle
                        )
                    finally:
                        # Cleanup guarantees /tmp/ won't leak
                        if os.path.exists(local_path):
                            os.remove(local_path)
        except Exception as e:
            logger.error(f"Error polling SQS: {str(e)}")
        
        await asyncio.sleep(1)
