"""Auto-generated Temporal type definitions."""

from __future__ import annotations
from dataclasses import dataclass, field
from typing import Any, Optional
from datetime import datetime
from enum import Enum

@dataclass
class Money:
    amount: int
    currency: str

@dataclass
class OrderItem:
    sku: str
    quantity: int
    unit_price: Money

@dataclass
class OrderInput:
    order_id: str
    customer_id: str
    items: list[str]
    total: Money

class OrderStatus(str, Enum):
    PENDING = "pending"
    PAYMENT_CAPTURED = "payment_captured"
    SHIPPED = "shipped"
    COMPLETED = "completed"
    CANCELLED = "cancelled"

@dataclass
class PaymentResult:
    transaction_id: str
    captured: bool

@dataclass
class ShipmentResult:
    tracking_number: str
    carrier: str
