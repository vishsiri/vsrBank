# 🏦 VsrBank (Cross-Server Banking System)

**VsrBank** is a high-performance, enterprise-grade banking solution for Minecraft networks. It is designed for **Data Integrity** and **Cross-Server Synchronization** using Redis and MySQL. The system prevents race conditions (duplication exploits) via distributed locking and Atomic SQL operations.

## ✨ Key Features

### 🛡️ Core Security & Integrity

* **Atomic Transactions:** Uses direct SQL relative updates (`balance = balance + ?`) to prevent race conditions.
* **Distributed Locking (Redlock):** Prevents simultaneous transaction requests across multiple servers using Redis.
* **ACID Compliance:** Ensures all database operations are wrapped in transactions (Commit/Rollback).
* **Internal Debounce:** Packet limiting to prevent spam-based exploits.

### 🌐 Cross-Server Sync

* **Real-time Synchronization:** Instant balance updates across BungeeCord/Velocity using **Redis Pub/Sub**.
* **Offline Support:** Transfer money to offline players safely.

### 💰 Gameplay Features

* **Tier System:** Upgrade bank levels to unlock higher balance caps and better interest rates.
* **Interest System:** Configurable compound interest (Online vs. Offline rates).
* **Transaction History:** View the last 10 transactions via GUI.
* **Visual GUI:** Fully customizable menus supporting **Hex Colors**, Gradients, and Custom Model Data.

### 🔌 Integration

* **Vault Compatible:** Functions as a standard economy provider.
* **PlaceholderAPI:** extensive placeholders for scoreboards/chat.
* **Discord Webhooks:** Alerts for suspicious activity and admin actions.

## 🛠️ Technology Stack

Built with maintainability and performance in mind using modern libraries:

* **Lombok:** Boilerplate reduction.
* **HikariCP:** High-performance JDBC connection pooling.
* **Jedis:** Redis client for Pub/Sub and Locking.
* **Revxrsal Lamp:** Annotation-driven command framework.
* **Triumph-GUI:** Fluent GUI creation.
* **ConfigLib:** Object-mapped configuration management.

---

## 📥 Installation

1. **Prerequisites:**
* Java 21+
* MySQL / MariaDB (Recommended) or SQLite
* Redis Server (Required for Cross-Server features)


2. **Setup:**
* Drop `VsrBank.jar` into the `plugins` folder.
* Start the server to generate `config.yml`.
* Configure your Database and Redis credentials.
* Restart the server.

---

## 💻 Developer API

VsrBank provides a robust **Asynchronous API** via `CompletableFuture`.

### Events

* `BankPreTransactionEvent` (Cancellable)
* `BankPostTransactionEvent`
* `BankLevelUpEvent`

**Developed with ❤️ by Visherryz**