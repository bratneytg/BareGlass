FROM python:3.12-slim
WORKDIR /srv
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app app
RUN useradd -r -u 1000 bg && mkdir -p /data && chown bg /data
USER bg
ENV BG_DB_PATH=/data/bareglass.db
EXPOSE 8000
# Un solo proceso: SQLite escribe de uno en uno. Para más potencia, pasar a PostgreSQL (ver docs/arquitectura-datos.md).
CMD ["uvicorn", "app.asgi:app", "--host", "0.0.0.0", "--port", "8000", "--proxy-headers"]
