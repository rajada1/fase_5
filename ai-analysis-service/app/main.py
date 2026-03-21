import asyncio
import logging
from fastapi import FastAPI
from app.api import health
from app.services.sqs_poller import start_polling

import sys
from pythonjsonlogger import jsonlogger

for handler in list(logging.root.handlers):
    logging.root.removeHandler(handler)
logHandler = logging.StreamHandler(sys.stdout)
formatter = jsonlogger.JsonFormatter('%(asctime)s %(levelname)s %(message)s', rename_fields={"levelname": "level", "asctime": "time"})
logHandler.setFormatter(formatter)
logging.root.addHandler(logHandler)
logging.root.setLevel(logging.INFO)

app = FastAPI(title="AI Analysis Service")

app.include_router(health.router)

@app.on_event("startup")
async def startup_event():
    # Start SQS Poller in the background
    asyncio.create_task(start_polling())
