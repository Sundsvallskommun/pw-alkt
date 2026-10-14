# PwAlkt

_PwAlkt runs the process models of the alcohol and tobacco unit (ALKT) in a shared Operaton process engine. Support
Management owns the errand, and this service owns the process behind it._

## Getting Started

### Prerequisites

- **Java 25 or higher**
- **Maven**
- **Git**
- **Operaton**
- **[Dependent Microservices](#dependencies)**

### Installation

1. **Clone the repository:**

   ```bash
   git clone git@github.com:Sundsvallskommun/pw-alkt.git
   ```
2. **Configure the application:**

   Before running the application, you need to set up configuration settings.
   See [Configuration](#configuration)

   **Note:** Ensure all required configurations are set; otherwise, the application may fail to start.

3. **Ensure dependent services are running:**

   Make sure the services below are up and accessible. See [Dependencies](#dependencies) for more details.

4. **Build and run the application:**

   ```bash
   mvn spring-boot:run
   ```

## Dependencies

This microservice depends on the following services:

- **Operaton**
  - **Purpose:** The process engine. The service deploys its models there on start and works the external tasks of
    tenant `ALKT`. Other services share the engine and its topic names.
  - **Repository:** [https://github.com/operaton/operaton](https://github.com/operaton/operaton)
- **Support Management**
  - **Purpose:** Owns the errand. Sends the errand events that start and move the processes, and receives the process
    state and the activity log.
  - **Repository:** [https://github.com/Sundsvallskommun/api-service-support-management](https://github.com/Sundsvallskommun/api-service-support-management)
- **Party Assets**
  - **Purpose:** Keeps the permits the processes create, change and end.
  - **Repository:** [https://github.com/Sundsvallskommun/api-service-party-assets](https://github.com/Sundsvallskommun/api-service-party-assets)
- **Licensed Business**
  - **Purpose:** Keeps the restaurant numbers of the premises.
  - **Repository:** [https://github.com/Sundsvallskommun/api-service-licensed-business](https://github.com/Sundsvallskommun/api-service-licensed-business)
- **Party**
  - **Purpose:** Looks up the organisation number of the permit holder, which licensed business keys the restaurant
    number on.
  - **Repository:** [https://github.com/Sundsvallskommun/api-service-party](https://github.com/Sundsvallskommun/api-service-party)
- **Templating**
  - **Purpose:** Renders the permit certificate and the inspection protocol.
  - **Repository:** [https://github.com/Sundsvallskommun/api-service-templating](https://github.com/Sundsvallskommun/api-service-templating)
- **Messaging**
  - **Purpose:** Posts incident alerts to Slack.
  - **Repository:** [https://github.com/Sundsvallskommun/api-service-messaging](https://github.com/Sundsvallskommun/api-service-messaging)

Ensure that these services are running and properly configured before starting this microservice.

## API Documentation

Access the API documentation via Swagger UI:

- **Swagger UI:** [http://localhost:8080/api-docs](http://localhost:8080/api-docs)

## Usage

### API Endpoints

Refer to the [API Documentation](#api-documentation) for detailed information on available endpoints.

### Example Request

Support Management posts every event on an errand to one endpoint, which answers `202`. The event starts a process,
moves it on or deletes it.

```bash
curl -X POST http://localhost:8080/2281/ALKT/process/errand-events \
  -H 'Content-Type: application/json' \
  -d '{
        "eventId": "5b2e8c41-9a7d-4f3e-b1c6-0d8e2f4a6c13",
        "eventType": "CREATE",
        "eventSubType": "ERRAND",
        "errandId": "9d1c3b5a-7e2f-4a6c-8b0d-1e3f5a7c9b2d",
        "processKey": "alcohol-serving",
        "startAllowed": true
      }'
```

## Processes

The `processKey` of the event names the process. The models are in `src/main/resources/processmodels`, one folder
per table below.

### Applications

|        Process key         |                   Errand                    |                               What the service does                                | Permit in party-assets |
|----------------------------|---------------------------------------------|------------------------------------------------------------------------------------|------------------------|
| `alcohol-serving`          | Serving of alcoholic beverages              | Checks the decision, creates the permit, chooses and assigns the restaurant number | `AlcoholServingPermit` |
| `alcohol-serving-addition` | Addition to an existing serving permit      | Checks the decision, finds the holder's restaurant number, creates the permit      | `AlcoholServingPermit` |
| `alcohol-serving-change`   | Change of an existing serving permit        | Checks the decision, drafts the change, updates the permit                         | `AlcoholServingPermit` |
| `tobacco-sales`            | Sale of tobacco products                    | Checks the decision, creates the permit                                            | `TobaccoSalesPermit`   |
| `tobacco-sales-change`     | Change of permit-regulated sale of tobacco  | Checks the decision, drafts the change, updates the permit                         | `TobaccoSalesPermit`   |
| `tobacco-sales-closure`    | Closure of permit-regulated sale of tobacco | Checks the permit, checks the decision, ends the permit                            | `TobaccoSalesPermit`   |

Every application tells the customer that the processing has started.

### Notifications

|              Process key              |                  Errand                   |         What the service does         |             Asset in party-assets             |
|---------------------------------------|-------------------------------------------|---------------------------------------|-----------------------------------------------|
| `catering-occasion`                   | Catering occasion                         | Makes the decision, creates the asset | `CateringOccasionNotification`                |
| `low-alcohol-beer-sales`              | Sale of low-alcohol beer                  | Makes the decision, creates the asset | `LowAlcoholBeerSalesNotification`             |
| `low-alcohol-beer-serving`            | Serving of low-alcohol beer               | Makes the decision, creates the asset | `LowAlcoholBeerServingNotification`           |
| `low-alcohol-beer-sales-and-serving`  | Sales and serving of low-alcohol beer     | Makes the decision, creates the asset | `LowAlcoholBeerSalesAndServingNotification`   |
| `e-cigarette-sales`                   | Sale of electronic cigarettes and refills | Makes the decision, creates the asset | `ECigaretteSalesNotification`                 |
| `tobacco-free-nicotine-product-sales` | Sale of tobacco-free nicotine products    | Makes the decision, creates the asset | `TobaccoFreeNicotineProductSalesNotification` |

### Inspections

|      Process key      |       Errand        |                               What the service does                               | Permit in party-assets |
|-----------------------|---------------------|-----------------------------------------------------------------------------------|------------------------|
| `external-inspection` | External inspection | Tells the customer, creates the protocol, creates the action errand when asked to | None                   |
| `internal-inspection` | Internal inspection | Tells the customer, creates the protocol, creates the action errand when asked to | None                   |
| `action-errand`       | Action errand       | Checks the decision                                                               | None yet               |

The action errand gets the title `Åtgärdsärende`, status `NEW`, priority `MEDIUM`, and the case worker assigned to
the inspection as reporter. An inspection without an assigned case worker or without a permit holder raises an
incident.

### Decision outcomes

|     Process     |                 Outcome                 |                                Result                                 |
|-----------------|-----------------------------------------|-----------------------------------------------------------------------|
| Applications    | `APPROVAL`, `APPROVAL_WITH_CONDITIONS`  | Creates, changes or ends the permit                                   |
| Applications    | `REJECTED`, `DISMISSED`, `INADMISSIBLE` | Ends without a permit                                                 |
| `action-errand` | `REPRIMAND`, `WARNING`, `REVOCATION`    | Ends the decision phase                                               |
| Any             | An outcome of another process           | Logs a refusal in the activity log and waits for a corrected decision |

### Process reconciliation

`process-reconciliation` runs every five minutes in the engine and reports processes that ended or stopped without
telling Support Management. It is not started by an event.

## Configuration

Configuration is crucial for the application to run successfully. Ensure all necessary settings are configured in
`application.yaml`.

### Key Configuration Parameters

- **Server Port:**

  ```yaml
  server:
    port: 8080
  ```
- **Integrations:**

  Every integration takes a base url and OAuth2 client credentials.

  ```yaml
  config:
    support-management:
      base-url: http://dependency_service_url
      token-uri: http://token_url
      client-id: client-id
      client-secret: client-secret
    # the same for operaton, party-assets, licensed-business, party, templating and messaging
    messaging:
      slack:
        token: slack-token
        channel: slack-channel
  ```
- **Action errand:**

  ```yaml
  action-errand:
    label-id: a8561314-5335-40db-80a9-b7f426a4baa2
  ```

  The label the action errand gets. Its metadata in Support Management names the process key `action-errand`. Label ids
  differ between environments.

- **Texts for the customer and the activity log:**

  ```yaml
  customer-message:
    texts:
      processing-started: Vi har nu börjat handlägga ditt ärende.
  process-log:
    steps:
      "[external_task_check_decision]":
        done: Beslutet i ärendet har kontrollerats
        retry: Beslutet kunde inte kontrolleras just nu, ett nytt försök görs automatiskt
        failed: Beslutet kunde inte kontrolleras. Processen har stannat och felet behöver åtgärdas
        rejected: Beslutets utfall hör inte till den här typen av ärende. Ändra beslutet så fortsätter processen
  ```

  Every step and phase of the models needs its texts, or the service does not start. `application-it.yaml` holds the
  full set. A key with an underscore must be in brackets.

### Additional Notes

- **Support Management:**

  The namespace needs the status `NEW`, the decision outcomes above and the label of the action errand.

- **Application Profiles:**

  Use Spring profiles (`dev`, `prod`, etc.) to manage different configurations for different environments.

- **Logging Configuration:**

  Adjust logging levels if necessary.

## Contributing

Contributions are welcome! Please
see [CONTRIBUTING.md](https://github.com/Sundsvallskommun/.github/blob/main/.github/CONTRIBUTING.md) for guidelines.

## License

This project is licensed under the [MIT License](LICENSE).

## Code status

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_pw-alkt&metric=alert_status)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_pw-alkt)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_pw-alkt&metric=reliability_rating)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_pw-alkt)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_pw-alkt&metric=security_rating)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_pw-alkt)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_pw-alkt&metric=sqale_rating)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_pw-alkt)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_pw-alkt&metric=vulnerabilities)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_pw-alkt)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_pw-alkt&metric=bugs)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_pw-alkt)

---

© 2026 Sundsvalls kommun
