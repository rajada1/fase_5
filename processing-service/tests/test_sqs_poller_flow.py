"""Tests for the processing service SQS poller flow."""
import asyncio
import json
import pytest
from unittest.mock import MagicMock, patch
from app.services import sqs_poller


def _make_sqs_message(data: dict) -> dict:
    return {
        'Messages': [{
            'ReceiptHandle': 'test-receipt',
            'Body': json.dumps({'Message': json.dumps(data)})
        }]
    }


@pytest.fixture
def mock_sqs(mocker):
    client = MagicMock()
    mocker.patch("app.services.sqs_poller.get_sqs_client", return_value=client)
    return client


@pytest.fixture
def mock_sns(mocker):
    mocker.patch("app.services.sqs_poller.publish_diagram_processed_event")
    mocker.patch("app.services.sqs_poller.publish_processing_failed_event")
    return mocker


def test_start_polling_success_path_should_publish_and_delete(mock_sqs, mock_sns, mocker):
    """Valid message should check S3, publish processed event, and delete message."""
    mocker.patch("app.services.sqs_poller.check_file_exists", return_value=True)
    mocker.patch("app.services.sqs_poller.should_skip_duplicate", return_value=False)

    mock_sqs.receive_message.side_effect = [
        _make_sqs_message({"diagramId": "diag-1", "s3Key": "diagrams/diag-1.png", "eventType": "FILE_UPLOADED"}),
        KeyboardInterrupt,
    ]

    with pytest.raises(KeyboardInterrupt):
        asyncio.run(sqs_poller.start_polling())

    from app.services.sqs_poller import publish_diagram_processed_event
    publish_diagram_processed_event.assert_called_once_with("diag-1", "diagrams/diag-1.png", None)
    mock_sqs.delete_message.assert_called_once()


def test_start_polling_file_not_found_should_publish_failure(mock_sqs, mock_sns, mocker):
    """If file doesn't exist in S3, should publish failure event."""
    mocker.patch("app.services.sqs_poller.check_file_exists", return_value=False)
    mocker.patch("app.services.sqs_poller.should_skip_duplicate", return_value=False)

    mock_sqs.receive_message.side_effect = [
        _make_sqs_message({"diagramId": "diag-1", "s3Key": "diagrams/missing.png", "eventType": "FILE_UPLOADED"}),
        KeyboardInterrupt,
    ]

    with pytest.raises(KeyboardInterrupt):
        asyncio.run(sqs_poller.start_polling())

    from app.services.sqs_poller import publish_processing_failed_event
    publish_processing_failed_event.assert_called_once()
    mock_sqs.delete_message.assert_called_once()


def test_start_polling_invalid_payload_should_publish_failure_and_delete(mock_sqs, mock_sns, mocker):
    """Message without s3Key should publish failure and delete."""
    mocker.patch("app.services.sqs_poller.should_skip_duplicate", return_value=False)

    mock_sqs.receive_message.side_effect = [
        _make_sqs_message({"diagramId": "diag-1", "eventType": "FILE_UPLOADED"}),
        KeyboardInterrupt,
    ]

    with pytest.raises(KeyboardInterrupt):
        asyncio.run(sqs_poller.start_polling())

    from app.services.sqs_poller import publish_processing_failed_event
    publish_processing_failed_event.assert_called_once()
    mock_sqs.delete_message.assert_called_once()


def test_start_polling_malformed_json_should_delete_and_not_publish_failure(mock_sqs, mock_sns, mocker):
    """Malformed JSON should be deleted without publishing failure."""
    mock_sqs.receive_message.side_effect = [
        {'Messages': [{'ReceiptHandle': 'r1', 'Body': 'not-json'}]},
        KeyboardInterrupt,
    ]

    with pytest.raises(KeyboardInterrupt):
        asyncio.run(sqs_poller.start_polling())

    mock_sqs.delete_message.assert_called_once()
