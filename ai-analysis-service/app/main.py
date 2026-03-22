import asyncio
import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI
from app.api import health
from app.services.sqs_poller import start_polling

import sys
from pythonjsonlogger.json import JsonFormatter

for handler in list(logging.root.handlers):
    logging.root.removeHandler(handler)
logHandler = logging.StreamHandler(sys.stdout)
formatter = JsonFormatter('%(asctime)s %(levelname)s %(message)s', rename_fields={"levelname": "level", "asctime": "time"})
logHandler.setFormatter(formatter)
logging.root.addHandler(logHandler)
logging.root.setLevel(logging.INFO)

@asynccontextmanager
async def lifespan(app: FastAPI):
    poller_task = asyncio.create_task(start_polling())
    try:
        yield
    finally:
        poller_task.cancel()
        try:
            await poller_task
        except asyncio.CancelledError:
            pass


app = FastAPI(title="AI Analysis Service", lifespan=lifespan)

app.include_router(health.router)
