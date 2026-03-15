# RFC: Temporal Interface Schema (TIS)

**RFC Number:** TIS-0001
**Title:** Temporal Interface Schema Specification
**Version:** 1.1
**Status:** Draft
**Author:** Community Draft
**Date:** 2026-03-15

---

# Abstract

The **Temporal Interface Schema (TIS)** defines a YAML-based specification for describing **Temporal workflows, activities, workers, client interfaces, and execution pipelines** in a language-neutral format.

The purpose of this specification is to enable **tooling that can generate Temporal client stubs, worker stubs, and shared models automatically** from a single declarative schema.

This document follows an **RFC-style specification format** similar to specifications such as OpenAPI and AsyncAPI.

---

# Table of Contents

1. Introduction
2. Terminology
3. Conventions Used in This Document
4. Goals and Design Principles
5. Document Structure
6. Schema Types
7. Models
8. Workers
9. Workflows
10. Activities
11. Clients
12. Pipeline Workflows
13. Stage Mapping Rules
14. Validation Rules
15. Generator Expectations
16. Security Considerations
17. Extensibility
18. Example Document

---

# 1. Introduction

Temporal applications often require:

* Workflow interfaces
* Activity interfaces
* Client invocation methods
* Cross-language compatibility
* Pipeline orchestration logic

Developers typically write these definitions **in code**, which can lead to duplication across languages.

The **Temporal Interface Schema (TIS)** provides a **declarative interface description** that allows these definitions to be expressed once and used to generate:

* Worker implementations
* Client SDKs
* Shared data models
* Workflow orchestration scaffolding

---

# 2. Terminology

| Term     | Meaning                                          |
| -------- | ------------------------------------------------ |
| Workflow | A Temporal workflow definition                   |
| Activity | A Temporal activity executed by a worker         |
| Worker   | A service hosting workflows and activities       |
| Client   | A service invoking workflows                     |
| Pipeline | A structured workflow composed of ordered stages |
| Model    | A reusable data schema definition                |
| Stage    | A step within a pipeline workflow                |

---

# 3. Conventions Used in This Document

The key words **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, and **MAY** are to be interpreted as described in **RFC 2119**.

| Keyword  | Meaning                  |
| -------- | ------------------------ |
| MUST     | Required for compliance  |
| MUST NOT | Prohibited               |
| SHOULD   | Recommended but optional |
| MAY      | Optional                 |

---

# 4. Goals and Design Principles

The Temporal Interface Schema is designed to:

1. Provide **language-neutral workflow definitions**
2. Support **multi-language Temporal environments**
3. Enable **automated code generation**
4. Support **sync, async, and pipeline execution models**
5. Maintain **simple YAML readability**

Non-goals:

* Replace Temporal SDKs
* Define runtime behavior
* Provide workflow scheduling semantics

---

# 5. Document Structure

A valid Temporal Interface Schema document MUST contain the following top-level structure:

```yaml
version: 1.1
namespace: example-namespace

types:
models:
workers:
clients:
```

---

# 6. Schema Types

Primitive types MAY be defined under the `types` section.

### Allowed primitive types

```
string
integer
boolean
number
object
array
```

Example:

```yaml
types:
  primitives:
    - string
    - integer
    - boolean
    - number
    - object
    - array
```

Generators SHOULD support these primitives.

---

# 7. Models

Models define reusable schemas for workflow and activity inputs and outputs.

Example:

```yaml
models:
  FileRequest:
    type: object
    properties:
      filename:
        type: string
      bucket:
        type: string
```

## Model Requirements

A model:

* MUST define `type`
* SHOULD define `properties` if `type` is `object`
* MAY define `required`
* MAY define `description`

Example:

```yaml
models:
  FileInspectionResult:
    type: object
    required:
      - fileType
      - fileSize
    properties:
      fileType:
        type: string
      fileSize:
        type: integer
```

---

# 8. Workers

Workers represent Temporal worker services.

## Required fields

| Field     | Required |
| --------- | -------- |
| name      | MUST     |
| language  | MUST     |
| taskQueue | MUST     |

Example:

```yaml
workers:
  - name: file-inspector-worker
    language: python
    taskQueue: file-inspector-queue
```

### Worker fields

| Field      | Description          |
| ---------- | -------------------- |
| workflows  | Workflow definitions |
| activities | Activity definitions |

---

# 9. Workflows

A workflow describes a Temporal workflow interface.

## Required fields

| Field         | Requirement |
| ------------- | ----------- |
| name          | MUST        |
| interfaceType | MUST        |
| input         | MUST        |
| output        | MUST        |

### `interfaceType`

Defines execution behavior.

Allowed values:

```
sync
async
pipeline
```

### Meaning

| Type     | Behavior                               |
| -------- | -------------------------------------- |
| sync     | Client waits for final result          |
| async    | Client receives workflow handle/status |
| pipeline | Workflow composed of ordered stages    |

Example:

```yaml
- name: InspectFileSync
  interfaceType: sync
  input:
    ref: FileRequest
  output:
    ref: FileInspectionResult
```

---

# 10. Activities

Activities represent executable worker tasks.

Example:

```yaml
activities:
  - name: detectFileType
    executionType: sync
```

### Required fields

| Field         | Requirement |
| ------------- | ----------- |
| name          | MUST        |
| executionType | MUST        |
| input         | MUST        |
| output        | MUST        |

### Allowed execution types

```
sync
async
```

---

# 11. Clients

Clients define workflow invocation interfaces.

Example:

```yaml
clients:
  - name: document-client
    language: java
```

### Client fields

| Field     | Description                |
| --------- | -------------------------- |
| workflows | List of callable workflows |

Example workflow invocation:

```yaml
workflows:
  - name: InspectFileAsync
    interfaceType: async
    taskQueue: file-inspector-queue
```

---

# 12. Pipeline Workflows

Pipeline workflows describe ordered execution stages.

Example:

```yaml
interfaceType: pipeline
```

Pipeline workflows MUST define `stages`.

Example:

```yaml
stages:
  - name: detect-type
    activity: detectFileType
```

---

# 13. Pipeline Stage Types

Allowed stage types:

```
activity
transform
```

Future extensions MAY include:

```
workflow
signal
query
```

### Activity Stage

```yaml
- name: detect-type
  type: activity
  activity: detectFileType
```

### Transform Stage

Transforms combine previous outputs.

Example:

```yaml
- name: build-result
  type: transform
  output:
    type: object
    properties:
      fileType:
        from: stages.detectedType.fileType
```

---

# 14. Stage Mapping Rules

Stage inputs MAY reference:

```
workflow.input
workflow.input.<field>
stages.<stageKey>
stages.<stageKey>.<field>
```

Example:

```yaml
input:
  from: workflow.input
```

---

# 15. Validation Rules

A schema document is valid if:

1. `version` MUST be present.
2. `namespace` MUST be present.
3. `workers` MUST be present.
4. Each worker MUST define:

   * name
   * language
   * taskQueue
5. Workflows MUST define:

   * name
   * interfaceType
   * input
   * output
6. Pipeline workflows MUST define stages.
7. Activities MUST define:

   * name
   * executionType
   * input
   * output
8. Model references MUST exist.
9. Stage names MUST be unique within a workflow.
10. `ref` and `type` MUST NOT coexist in the same schema object.

---

# 16. Generator Expectations

Tools implementing this specification SHOULD generate:

| Component  | Output                       |
| ---------- | ---------------------------- |
| Models     | Shared data classes          |
| Workflows  | Temporal workflow interfaces |
| Activities | Activity interfaces          |
| Clients    | Workflow invocation methods  |

Example generated Java interface:

```java
@WorkflowMethod
FileInspectionResult inspectFile(FileRequest request);
```

Example async client:

```java
CompletableFuture<PipelineStatus> inspectFileAsync(FileRequest request);
```

---

# 17. Security Considerations

This specification does not define runtime execution or authorization policies.

Implementations SHOULD ensure:

* Workflow inputs are validated
* Activity execution is authenticated
* Client calls enforce authorization

Generators MAY integrate with security frameworks such as:

* OAuth
* OIDC
* RBAC systems

---

# 18. Extensibility

Future extensions MAY include:

* retry policies
* workflow timeouts
* signal definitions
* query interfaces
* workflow versioning
* search attributes
* cron schedules

Example extension:

```yaml
retryPolicy:
  maximumAttempts: 3

timeouts:
  startToClose: 30s

signals:
  - cancel
```

---

# 19. Example Schema

```yaml
version: 1.1
namespace: document-processing

models:
  FileRequest:
    type: object
    properties:
      filename:
        type: string
      bucket:
        type: string

workers:
  - name: file-worker
    language: python
    taskQueue: file-processing

    workflows:
      - name: InspectFileSync
        interfaceType: sync
        input:
          ref: FileRequest
        output:
          ref: FileInspectionResult

    activities:
      - name: detectFileType
        executionType: sync
        input:
          ref: FileRequest
        output:
          ref: FileTypeResult

clients:
  - name: document-client
    language: java
    workflows:
      - name: InspectFileSync
        interfaceType: sync
        taskQueue: file-processing
        input:
          ref: FileRequest
        output:
          ref: FileInspectionResult
```

---

# 20. Future Work

Future RFCs may define:

* **TIS Code Generation Standard**
* **TIS JSON Schema Validation**
* **Temporal Pipeline Execution Semantics**
* **Cross-Worker Workflow Composition**
* **Observability and tracing metadata**

---

