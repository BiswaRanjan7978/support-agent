# AI Email Support Agent

An autonomous customer-support email agent built with **Java 21, Spring Boot, Spring AI, OpenAI, and the Model Context Protocol (MCP)**. It monitors a support inbox, gathers verified customer/order/product information through MCP tools, decides on an appropriate response, records the interaction, and replies by email.

> This repository contains the email-agent/client application. The companion `mcp-server` project provides the business-data tools and MySQL persistence used by this application.

## What it does

- Monitors a Mailpit inbox for unread support emails on a configurable polling interval.
- Uses Spring AI `ChatClient` and an OpenAI chat model to understand each incoming message.
- Discovers and calls tools exposed by the companion MCP server rather than directly querying business data.
- Uses a system prompt to guide customer lookup, order/product investigation, warranty and billing checks, refund decisions, and ticket logging.
- Sends an email reply to the original sender and keeps the reply in the original email thread when message headers are available.
- Marks successfully fetched messages as read; failed processing is reset to unread so it can be retried on a later poll.
- Provides a small endpoint for injecting test emails into the local Mailpit inbox.

## Architecture

```text
Customer email
     |
     v
Mailpit (SMTP :1025 / Web UI + REST API :8025)
     |
     v
InboxMonitor -- polls unread messages --> MailpitClient
     |
     v
SupportAgent (Spring AI ChatClient + OpenAI)
     |
     +---- MCP client connection ----> MCP Server (:8090/mcp)
     |                                  |
     |                                  +--> MySQL support data
     |                                  +--> Customer/order/product queries
     |                                  +--> Refund and support-ticket actions
     |
     v
SupportMailSender
     |
     v
Customer email reply (captured by Mailpit)
```

The LLM is instructed to ground decisions in MCP tool results. The prompt explicitly warns against inventing customer/order facts and against issuing a refund without evidence.

## Technology stack

- Java 21
- Spring Boot 4.1.0
- Spring AI 2.0.0
- Spring AI OpenAI model starter
- Spring AI MCP client starter (Streamable HTTP)
- Spring Web MVC and Spring Boot Mail
- Mailpit for local SMTP, inbox UI, and REST API
- Maven Wrapper

## Prerequisites

- JDK 21
- Docker Desktop with Docker Compose
- Git (optional)
- An OpenAI API key
- The companion MCP server project running and reachable at `http://localhost:8090/mcp`

## Configuration

The application reads its configuration from `src/main/resources/application.properties`.

| Property | Current default | Purpose |
|---|---|---|
| `spring.ai.openai.api-key` | `${OPENAI_API_KEY}` | OpenAI API key; provide it through an environment variable |
| `spring.mail.host` | `localhost` | Mailpit SMTP host |
| `spring.mail.port` | `1025` | Mailpit SMTP port |
| `support-agent.inbox.base-url` | `http://localhost:8025` | Mailpit REST API base URL |
| `support-agent.inbox.address` | `support@example.com` | Monitored support mailbox |
| `support-agent.inbox.poll-interval` | `10000` | Inbox polling delay in milliseconds |
| `support-agent.inbox.batch-size` | `50` | Maximum unread messages fetched per poll |
| `spring.ai.mcp.client.streamable-http.connections.support-agent.url` | `http://localhost:8090` | MCP server base URL |
| `spring.ai.mcp.client.streamable-http.connections.support-agent.endpoint` | `/mcp` | MCP endpoint path |

Do not commit API keys or other secrets. Supply `OPENAI_API_KEY` through your shell or IDE run configuration.

### Set the OpenAI key on Windows PowerShell

```powershell
$env:OPENAI_API_KEY="your-openai-api-key"
```

This sets the variable for the current PowerShell session. Add it to your IntelliJ Run Configuration environment variables if you launch the application from IntelliJ.

## Run locally

Start the companion MCP server first. Its MySQL database and MCP endpoint must be available before this application can use its tools.

### 1. Start Mailpit

From this project directory:

```bash
docker compose up -d
```

Mailpit exposes:

- Web inbox: <http://localhost:8025>
- SMTP: `localhost:1025`

### 2. Start the companion MCP server

In the `mcp-server` project directory, follow that project's README. By default, it exposes the Streamable HTTP MCP endpoint at `http://localhost:8090/mcp` and connects to MySQL on host port `3307`.

### 3. Set the OpenAI API key

PowerShell:

```powershell
$env:OPENAI_API_KEY="your-openai-api-key"
```

### 4. Run the email agent

From this project's root directory:

```bash
./mvnw spring-boot:run
```

On Windows Command Prompt / PowerShell, you can also use:

```powershell
.\mvnw.cmd spring-boot:run
```

The Spring Boot application uses the default HTTP port `8080`.

## Send a test email

The application exposes `POST /seed-mail` to send a test message into the Mailpit inbox. The agent will pick it up on its next poll.

```bash
curl -X POST "http://localhost:8080/seed-mail?from=sarah.mitchell@example.com&subject=Order%20question&body=Hi%2C%20where%20is%20my%20order%3F"
```

You can also use the defaults:

```bash
curl -X POST http://localhost:8080/seed-mail
```

Open <http://localhost:8025> to inspect the incoming message and the agent's reply. The default support inbox address is `support@example.com`.

## Main code areas

```text
src/main/java/com/ai/support/agent/
├── client/       # Mailpit REST API client and response DTOs
├── config/       # Inbox configuration properties
├── controller/   # POST /seed-mail test endpoint
├── model/        # IncomingEmail and AgentResponse models
├── service/      # Inbox polling, AI orchestration, email handling and sending
└── SupportAgentApplication.java

src/main/resources/
├── application.properties
└── prompts/
    └── support-agent-system.st
```

## Important behavior and safety notes

- The MCP server is the source of business/customer/order data; this application does not contain the business database repositories.
- The system prompt requires the agent to look up facts before making claims and to log each interaction as a support ticket.
- Refunds are initiated through the MCP server's `issue_refund` tool. Review and test the companion server's action implementation before connecting this demo to any real payment or customer environment.
- Mailpit is a local development/testing mail server, not a production mailbox provider.
- The included Mailpit data directory is mounted at `./data` and persists local test messages.

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
| OpenAI authentication error | Confirm `OPENAI_API_KEY` is set in the same environment used to launch the app |
| MCP tools are unavailable | Start the companion MCP server and verify `http://localhost:8090/mcp` is configured correctly |
| Mailpit connection refused | Run `docker compose up -d` and check that ports `1025` and `8025` are free |
| Test email is not processed | Check the Mailpit inbox, support mailbox address, polling interval, and application logs |
| Port already in use | Stop the process using the port or adjust the corresponding application/Compose configuration |

## Project status

This is a local development/demo project illustrating an email-driven AI support workflow with Spring AI and MCP. Add production-grade authentication, authorization, audit controls, human approval for sensitive actions, monitoring, and operational safeguards before production use.
