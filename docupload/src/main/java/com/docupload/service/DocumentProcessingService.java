package com.docupload.service;

import com.docupload.model.DocumentProcessingResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class DocumentProcessingService {

    private static final String EXTRACTED_TEXT_TEMPLATE = """
            ══════════════════════════════════════════════════════════
            DOCUMENT EXTRACTION REPORT
            ══════════════════════════════════════════════════════════
            
            FILE METADATA
            ─────────────
            Original File   : %s
            File Size       : %s
            Content Type    : %s
            Job ID          : %s
            
            ══════════════════════════════════════════════════════════
            EXTRACTED CONTENT
            ══════════════════════════════════════════════════════════
            
            EXECUTIVE SUMMARY
            ─────────────────
            This document contains critical project information regarding the Q4 2024
            Strategic Initiative for Digital Transformation across all business units.
            The content has been fully parsed, OCR-processed, and indexed for downstream
            consumption by enterprise search and workflow automation systems.
            
            SECTION 1 — PROJECT OVERVIEW
            ─────────────────────────────
            The Digital Transformation Program (DTP) was initiated in response to board-
            level directives to modernize core infrastructure by Q2 2025. Estimated total
            investment stands at $4.2M with projected ROI of 340%% over a 36-month horizon.
            
            Key stakeholders include the CTO office, VP of Engineering, Director of Product,
            and external consulting partner Deloitte Digital. Weekly steering committee
            reviews are scheduled every Thursday at 09:00 EST.
            
            SECTION 2 — TECHNICAL SPECIFICATIONS
            ──────────────────────────────────────
            The proposed architecture leverages a cloud-native microservices stack deployed
            on AWS EKS (Kubernetes). Services communicate via Apache Kafka event streams with
            a target throughput of 50,000 messages/second. Data persistence uses PostgreSQL 16
            with read replicas and Redis 7.2 for L2 cache.
            
              • API Gateway         : AWS API Gateway v2 (HTTP API)
              • Authentication      : OAuth 2.0 + OIDC via Keycloak 24.x
              • Service Mesh        : Istio 1.21 with mTLS enforced
              • Observability       : OpenTelemetry → Grafana Cloud
              • CI/CD Pipeline      : GitHub Actions → ArgoCD → EKS
            
            SECTION 3 — RISK REGISTER
            ──────────────────────────
            ID    Risk Description                          Likelihood   Impact    Owner
            R-01  Key personnel departure (Lead Architect)  Medium       Critical  HR / CTO
            R-02  Third-party API deprecation (v1 sunset)   High         High      Platform
            R-03  Regulatory compliance gap (SOC 2 Type II) Low          Critical  Security
            R-04  Budget overrun due to scope creep         Medium       High      PMO
            R-05  Integration delay from legacy ERP system  High         Medium    IT Ops
            
            SECTION 4 — TIMELINE & MILESTONES
            ────────────────────────────────────
            Phase 1 (Jan – Mar 2025): Discovery & Architecture Design          ✓ COMPLETE
            Phase 2 (Apr – Jun 2025): Core Platform Build & Unit Testing       ⚙ IN PROGRESS
            Phase 3 (Jul – Sep 2025): Integration Testing & UAT                ○ PENDING
            Phase 4 (Oct – Dec 2025): Production Rollout & Hypercare           ○ PENDING
            
            SECTION 5 — SIGN-OFF
            ──────────────────────
            Prepared by   : Jane R. Holloway, Principal Solutions Architect
            Reviewed by   : Marcus T. Webb, VP Engineering
            Approved by   : Sandra L. Chen, Chief Technology Officer
            Date          : March 12, 2025
            Classification: INTERNAL — CONFIDENTIAL
            
            ══════════════════════════════════════════════════════════
            END OF EXTRACTION — Processed in %d ms
            ══════════════════════════════════════════════════════════
            """;

    @Async
    public CompletableFuture<DocumentProcessingResult> processDocument(MultipartFile file) {
        long startTime = System.currentTimeMillis();
        String jobId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        try {
            // ── Stage 1: Simulated virus scan (1.5s) ──────────────────────────────
            Thread.sleep(1_500);

            // ── Stage 2: Simulated OCR / parsing (2.5s) ───────────────────────────
            Thread.sleep(2_500);

            // ── Stage 3: Simulated NLP extraction & indexing (1.5s) ───────────────
            Thread.sleep(1_500);

            long processingTimeMs = System.currentTimeMillis() - startTime;

            String extractedText = String.format(
                    EXTRACTED_TEXT_TEMPLATE,
                    file.getOriginalFilename(),
                    formatFileSize(file.getSize()),
                    file.getContentType(),
                    jobId,
                    processingTimeMs
            );

            DocumentProcessingResult result = new DocumentProcessingResult(
                    jobId,
                    file.getName(),
                    file.getOriginalFilename(),
                    file.getSize(),
                    file.getContentType(),
                    extractedText,
                    processingTimeMs,
                    "SUCCESS"
            );

            return CompletableFuture.completedFuture(result);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long processingTimeMs = System.currentTimeMillis() - startTime;
            DocumentProcessingResult errorResult = new DocumentProcessingResult(
                    jobId, file.getName(), file.getOriginalFilename(),
                    file.getSize(), file.getContentType(),
                    "Processing was interrupted.", processingTimeMs, "ERROR"
            );
            return CompletableFuture.completedFuture(errorResult);
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024));
    }
}
