# AI Service

FastAPI chat service for the Order Delivery Tracker. It uses Google ADK for
chat and live order tools, and Chroma with sentence-transformer embeddings to
retrieve delivery policy and FAQ information.

## Requirements

- Python 3.11 or newer
- A Gemini API key and a supported Gemini model name
- The Spring Boot order service running at `http://localhost:8080` for live
  order status, driver location, and ETA requests

Policy and FAQ questions use the text files in `data/` and do not require the
Spring Boot service.

## Setup on Windows

Run these commands from the `ai-service` directory:

```powershell
py -3.11 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -r requirements.txt
```

Create an `.env` file in this directory with your own values:

```dotenv
GEMINI_API_KEY=your-gemini-api-key
GEMINI_MODEL=your-supported-gemini-model
ORDER_SERVICE_URL=http://localhost:8080
```

Keep `.env` private; it is excluded from Git. `ORDER_SERVICE_URL` is optional
and defaults to `http://localhost:8080`.

## Run

From the `ai-service` directory, start the development server:

```powershell
python -m uvicorn app.main:app --reload
```

The service listens at `http://127.0.0.1:8000`.

- Health check: `http://127.0.0.1:8000/health`
- Interactive API documentation: `http://127.0.0.1:8000/docs`
- Chat endpoint: `POST http://127.0.0.1:8000/chat`

On first use, `sentence-transformers` downloads the embedding model, and the
service creates a local Chroma index in `chroma_db/`. The generated index and
SQLite session database are local runtime data and are excluded from Git.

## Chat Request

Send a message and the signed-in user's raw JWT access token. The service adds
the `Bearer` authorization scheme when calling the Spring Boot backend.

```json
{
  "message": "Where is order 6?",
  "access_token": "your-jwt-access-token"
}
```

The endpoint returns a JSON object containing the assistant's `response`.
Use `/docs` to submit a request interactively. A valid user token is needed
for live order tools; policy and FAQ answers are retrieved from the local
documents in `data/`.