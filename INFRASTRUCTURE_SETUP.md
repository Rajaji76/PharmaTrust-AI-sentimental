# PharmaTrust Infrastructure Setup Guide

## Prerequisites

- Java 21
- Maven 3.8+
- Docker & Docker Compose
- PostgreSQL 15 (or use Docker)
- RabbitMQ 3.x (or use Docker)
- Redis 7.x (or use Docker)

## Quick Start with Docker

### 1. Start Infrastructure Services

```bash
# Start PostgreSQL, RabbitMQ, Redis, and LocalStack
docker-compose up -d

# Verify all services are running
docker-compose ps

# View logs
docker-compose logs -f
```

### 2. Access Services

- **PostgreSQL**: `localhost:5432`
  - Database: `pharmatrust_db`
  - Username: `postgres`
  - Password: `5593`

- **RabbitMQ Management UI**: http://localhost:15672
  - Username: `guest`
  - Password: `guest`

- **Redis**: `localhost:6379`

- **LocalStack (AWS S3 Mock)**: `localhost:4566`

### 3. Build and Run Application

```bash
# Build the application
mvn clean install

# Run the application
mvn spring-boot:run

# Or run with custom profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## Environment Variables

Create a `.env` file in the project root:

```bash
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=pharmatrust_db
DB_USERNAME=postgres
DB_PASSWORD=5593

# JWT
JWT_SECRET=your-512-bit-secret-key-change-this-in-production-minimum-64-characters-required

# AWS S3
AWS_REGION=us-east-1
S3_BUCKET_NAME=pharmatrust-lab-reports
AWS_ACCESS_KEY=your-access-key
AWS_SECRET_KEY=your-secret-key

# Blockchain
BLOCKCHAIN_RPC_URL=http://localhost:8545
CONTRACT_ADDRESS=0x...
BLOCKCHAIN_PRIVATE_KEY=0x...

# RabbitMQ
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USER=guest
RABBITMQ_PASSWORD=guest

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# CORS
CORS_ORIGINS=http://localhost:3000,http://localhost:5173
```

## Database Migration

Flyway migrations run automatically on application startup.

### Manual Migration Commands

```bash
# Run migrations
mvn flyway:migrate

# Check migration status
mvn flyway:info

# Rollback (if needed)
mvn flyway:undo
```

## Testing

```bash
# Run all tests
mvn test

# Run integration tests only
mvn verify -P integration-tests

# Run with test containers
mvn test -Dspring.profiles.active=test
```

## Monitoring

- **Health Check**: http://localhost:8080/actuator/health
- **Metrics**: http://localhost:8080/actuator/metrics
- **Prometheus**: http://localhost:8080/actuator/prometheus
- **API Docs**: http://localhost:8080/swagger-ui.html

## Troubleshooting

### PostgreSQL Connection Issues

```bash
# Check if PostgreSQL is running
docker-compose ps postgres

# View PostgreSQL logs
docker-compose logs postgres

# Restart PostgreSQL
docker-compose restart postgres
```

### RabbitMQ Connection Issues

```bash
# Check RabbitMQ status
docker-compose ps rabbitmq

# Access RabbitMQ management UI
open http://localhost:15672
```

### Clear All Data

```bash
# Stop and remove all containers with volumes
docker-compose down -v

# Restart fresh
docker-compose up -d
```

## Production Deployment

See `DEPLOYMENT.md` for production deployment instructions.
