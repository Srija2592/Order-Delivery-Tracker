
from pathlib import Path

import chromadb
from sentence_transformers import SentenceTransformer

BASE_DIR = Path(__file__).resolve().parents[2]
DATA_DIR = BASE_DIR / "data"
DB_DIR = BASE_DIR / "chroma_db"

embedding_model = SentenceTransformer(
    "sentence-transformers/all-MiniLM-L6-v2"
)

client = chromadb.PersistentClient(path=str(DB_DIR))

collection = client.get_or_create_collection(
    name="delivery_knowledge"
)


def index_documents():
    documents = []
    ids = []

    for file_path in sorted(DATA_DIR.glob("*.txt")):
        text = file_path.read_text(encoding="utf-8")
        chunks = [
            chunk.strip()
            for chunk in text.split("\n\n")
            if chunk.strip()
        ]

        for index, chunk in enumerate(chunks):
            documents.append(chunk)
            ids.append(f"{file_path.stem}-{index}")

    if not documents:
        return

    embeddings = embedding_model.encode(
        documents,
        normalize_embeddings=True
    ).tolist()

    collection.upsert(
        ids=ids,
        documents=documents,
        embeddings=embeddings
    )



def retrieve_context(question: str, top_k: int = 3) -> str:
    index_documents()

    count = collection.count()
    if count == 0:
        return ""

    query_embedding = embedding_model.encode(
        [question],
        normalize_embeddings=True
    ).tolist()

    results = collection.query(
        query_embeddings=query_embedding,
        n_results=min(top_k, count)
    )

    documents = results.get("documents", [[]])[0]
    return "\n\n".join(documents)

