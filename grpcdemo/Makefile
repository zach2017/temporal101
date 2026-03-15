.PHONY: up down build logs clean

# Build and start server + client
up: build
	docker compose up

# Build images
build:
	docker compose build

# Tail logs
logs:
	docker compose logs -f

# Stop and remove containers
down:
	docker compose down

# Full cleanup (images + volumes)
clean:
	docker compose down --rmi all --volumes --remove-orphans
