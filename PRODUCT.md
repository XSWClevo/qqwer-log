# Product

## Register

product

## Users

Primary users are operations engineers, security analysts, platform administrators, and developers responsible for log ingestion, Vector agent management, ClickHouse-backed querying, alerting, and AI-assisted log analysis.

They work in an authenticated operational console, often during incident triage or routine platform maintenance. They need dense, reliable status, clear failure states, and fast paths from aggregate signals to underlying logs or configuration.

## Product Purpose

This product is a log analysis platform that connects frontend management, backend APIs, Vector agents, ClickHouse/PostgreSQL storage, alert monitors, and AI-assisted querying.

Success means users can understand platform health, inspect log datasets, create and manage Vector ingestion components, query logs naturally or manually, and respond to abnormal patterns without losing trust in the data shown.

## Brand Personality

Calm, precise, technical.

The interface should feel like a capable operations workbench: visually polished enough for product demos, but restrained enough for repeated daily use.

## Anti-references

Avoid marketing-style landing pages, oversized decorative hero layouts, large black-framed cards, generic neon dashboards, all-purple AI templates, and decorative effects that compete with operational data.

Avoid hiding empty or degraded data states. If a table or field capability is missing, show the supported fallback or a clear operational reason.

## Design Principles

1. Data credibility first: never invent or overstate metrics to make the dashboard look full.
2. Capability-driven views: render what the selected dataset can support, and downgrade locally when fields are missing.
3. Dense but legible: preserve operational scanning speed without creating visual clutter.
4. One interaction vocabulary: controls, cards, empty states, and warnings should behave consistently across Dashboard, Vector, Datasource, Agent, and Log Search surfaces.
5. Demo-ready, task-safe: the product can look like a technology command center, but the layout must still support real work.

## Accessibility & Inclusion

Target WCAG AA contrast for body text and controls. Preserve keyboard-accessible controls through Element Plus defaults where possible. Respect reduced-motion preferences for non-essential animations. Do not rely on color alone for severity or state; pair colors with text labels or structural cues.
