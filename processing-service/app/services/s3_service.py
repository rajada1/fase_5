import boto3
import os
from app.core.config import settings

def get_s3_client():
    return boto3.client(
        's3',
        region_name=settings.AWS_REGION,
        endpoint_url=settings.AWS_ENDPOINT_URL,
        aws_access_key_id="test",
        aws_secret_access_key="test"
    )

def download_file(s3_key: str, local_path: str):
    s3 = get_s3_client()
    s3.download_file(settings.S3_BUCKET_NAME, s3_key, local_path)
    return local_path
