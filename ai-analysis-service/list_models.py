from google import genai
import os
from dotenv import load_dotenv

load_dotenv()

api_key = os.getenv("GEMINI_API_KEY")
client = genai.Client(api_key=api_key)

try:
    print("Listando modelos disponíveis...")
    for model in client.models.list():
        print(f"Model: {model.name}")
except Exception as e:
    print(f"Erro ao listar modelos: {e}")
