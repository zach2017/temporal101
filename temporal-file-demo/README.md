# Temporal File Inspector Demo

This demo uses:

- **Temporal Server** for orchestration
- **Spring Boot 4.0.3** as the web app and Java worker
- **Python 3.14** as the second worker
- A static **Tailwind CSS** page with JavaScript buttons that send `{"filename":"..."}` to either worker

## What it does

1. You open the Java web app in the browser.
2. You enter a filename that exists in `shared-files/`.
3. The page posts JSON to the Java backend.
4. The Java backend starts a Temporal workflow.
5. The workflow routes the activity to either:
   - the **Java worker**, or
   - the **Python worker**
6. The selected worker reads the file from the shared folder and returns:
   - detected file type
   - file size in bytes

## Included sample files

- `sample.txt`
- `sample.pdf`
- `sample.png`

## Run it

```bash
docker compose up --build
```

## Open

- App: http://localhost:8080
- Temporal UI: http://localhost:8088

## Example JSON

```json
{
  "filename": "sample.txt"
}
```

## API endpoints

- `POST /api/inspect/java`
- `POST /api/inspect/python`

Example:

```bash
curl -X POST http://localhost:8080/api/inspect/java \
  -H "Content-Type: application/json" \
  -d '{"filename":"sample.txt"}'
```

## Notes

- Both workers read from the same mounted `shared-files/` directory.
- The Java app hosts both the UI and the Temporal workflow worker.
- The Python service only hosts an activity worker.
