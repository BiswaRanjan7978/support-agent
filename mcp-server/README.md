# Customer Support MCP Server

A Spring Boot **Model Context Protocol (MCP) server** for an e-commerce customer-support workflow. It exposes customer, order, product, payment, warranty, refund, and support-ticket capabilities as MCP tools. The tools are backed by Spring Data JPA and a MySQL database seeded with demo records.

This project is designed to be consumed by the companion `support-agent` application, which connects over **Streamable HTTP** at `http://localhost:8090/mcp`.

## Features

- Looks up customers by email address.
- Retrieves customer orders and individual order details.
- Searches products and retrieves product details by SKU.
- Detects possible duplicate charges by comparing captured payments with an order total.
- Checks product warranty eligibility using the order date and product warranty duration.
- Retrieves a customer's previous support-ticket history to help identify repeat issues.
- Creates refund records against an order/payment.
- Logs customer-support interactions as tickets.
- Initializes the local MySQL schema and demo data through Docker's MySQL initialization scripts.

## Architecture

```text
AI Support Agent / MCP Client
             |
             | Streamable HTTP
             v
     Spring Boot MCP Server
       localhost:8090/mcp
             |
      +------+------+
      |             |
 SupportQuery   SupportAction
    Tools           Tools
      |             |
      +------ JDBC/JPA ------+
                             |
                           MySQL
                       localhost:3307
```

The query tools provide the facts needed for a support decision. The action tools perform state-changing operations such as creating a refund record or logging a ticket.

## Technology stack

- Java 21
- Spring Boot 4.1.0
- Spring AI 2.0.0
- Spring AI MCP Server WebMVC starter
- Spring Web MVC
- Spring Data JPA / Hibernate
- MySQL 8-compatible database image (`mysql:latest` in Compose)
- MySQL Connector/J
- Maven Wrapper
- Docker Compose

## MCP tools

### Read/query tools

| MCP tool | Purpose |
|---|---|
| `lookup_customer_by_email` | Find a customer and return profile details such as name, preferred language, and loyalty tier |
| `get_customer_orders` | List orders for a customer email address |
| `get_order_by_number` | Retrieve a specific order, its items, and payment information |
| `search_products` | Search products by name or SKU fragment |
| `get_product_by_sku` | Retrieve product details using its SKU |
| `detect_duplicate_charges` | Compare captured charges with the order total and report potential overcharging |
| `check_warranty` | Check whether an order item is within its warranty period |
| `get_customer_ticket_history` | Retrieve prior support tickets for a customer |

### Action tools

| MCP tool | Purpose |
|---|---|
| `issue_refund` | Create a refund record for an order, optionally associated with a specific payment transaction reference |
| `log_support_ticket` | Record the original customer message, classification, language, and resolution |

> **Important:** `issue_refund` is a database-backed demo action. The code in this project creates/updates application records; do not assume it integrates with or transfers money through an external payment processor. Review the implementation before using it in a real environment.

## Prerequisites

- JDK 21
- Docker Desktop with Docker Compose
- Git (optional)
- Maven (optional; the Maven Wrapper is included)

## Database configuration

The current `compose.yaml` configures MySQL with these local development values:

| Setting | Value |
|---|---|
| Database | `mydatabase` |
| Application username | `myuser` |
| Application password | `secret` |
| Root password | `verysecret` |
| Host port | `3307` |
| Container port | `3306` |

The Spring datasource in `src/main/resources/application.properties` uses `localhost:3307` and the same application credentials.

For local development these credentials are convenient, but they are not suitable for production. Use environment-based secrets and a restricted database account outside local development.

## Run locally

### 1. Start MySQL

From the `mcp-server` project root:

```bash
docker compose up -d
```

Check container status:

```bash
docker compose ps
```

The Compose file mounts `db/init` into MySQL's initialization directory. On a **fresh, empty MySQL data volume**, the scripts create the schema and insert sample customers, products, orders, payments, refunds, and support tickets.

### 2. Run the MCP server

```bash
./mvnw spring-boot:run
```

On Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

The application listens on port `8090`. The MCP server uses the Streamable HTTP protocol and the companion client is configured to connect to:

```text
http://localhost:8090/mcp
```

### 3. Connect from the support-agent project

In the companion `support-agent` project's `application.properties`, the MCP client connection is configured as:

```properties
spring.ai.mcp.client.streamable-http.connections.support-agent.url=http://localhost:8090
spring.ai.mcp.client.streamable-http.connections.support-agent.endpoint=/mcp
```

Start this MCP server before starting the support-agent application so the agent can discover and call the tools.

## Database initialization and persistence

- The named Docker volume `mysql-data` persists the database when the container is stopped or recreated.
- MySQL initialization scripts run automatically only when the data directory is empty (typically the first start with a new volume).
- Editing `db/init/01-schema.sql` or `db/init/02-seed.sql` does not automatically re-run those scripts against an existing volume.
- Do not run `docker compose down -v` unless you intentionally want to delete the local database volume and all data stored in it.

If you need to rebuild demo data, back up any data you want to keep and deliberately recreate the volume only after confirming that deleting it is safe.

## Project structure

```text
mcp-server/
├── db/
│   └── init/
│       ├── 01-schema.sql       # Tables and schema
│       └── 02-seed.sql         # Demo data
├── src/main/java/com/ai/mcp/server/
│   ├── domain/                 # JPA entities and enums
│   ├── dto/                    # Tool response DTOs
│   ├── repository/             # Spring Data repositories
│   ├── tool/
│   │   ├── SupportQueryTools.java   # Read/query MCP tools
│   │   └── SupportActionTools.java  # Refund and ticket actions
│   └── McpServerApplication.java
├── src/main/resources/
│   └── application.properties
├── compose.yaml
└── pom.xml
```

## Run tests

```bash
./mvnw test
```

Windows:

```powershell
.\mvnw.cmd test
```

## Troubleshooting

| Symptom | What to check |
|---|---|
| Docker reports host port `3306` is unavailable | The Compose file maps host `3307` to container `3306`; check whether host port `3307` is already occupied |
| MySQL access denied | Confirm the username/password/database match the existing MySQL volume, not just the current Compose environment variables |
| Schema or seed data is missing | Initialization SQL runs only on a fresh data volume; inspect the MySQL logs and existing volume state |
| MCP client cannot connect | Confirm the server is running on port `8090` and the client URL/endpoint are `http://localhost:8090` and `/mcp` |
| Database connection refused | Run `docker compose ps`, wait for the MySQL health check, and verify port `3307` is reachable |

## Project status

This is a local development/demo MCP server for learning and prototyping. Before production use, add authentication and authorization, validate and constrain sensitive actions, implement idempotency and audit controls for refunds, secure secrets, and add observability and operational monitoring.
