Distributed File Sharing System (Java RMI + Sockets)
Overview
A scalable system for secure file sharing across multiple departments (Development, QA, Design). It combines Sockets (Client↔Coordinator) and Java RMI (Coordinator↔Node) to manage user authentication, permission enforcement, load balancing, and automated inter-node synchronization.

Features
User Management: Manager-led user registration; token-based login.

Access Control: Employees can ADD/UPDATE/DELETE only within their department; VIEW is unrestricted.

Load Balancing: Coordinator routes commands to the least-loaded node in a department.

Hybrid Communication:

Client–Coordinator: TCP Sockets + Java serialization.

Coordinator–Node: Java RMI.

File Locking: Ensures safe concurrent access via FileLock.

Auto-Sync: Nodes synchronize missing files every 60 seconds.

Retry Mechanism: Coordinator queues failed requests and retries every 30 seconds.


Architecture
ClientSimulator (Socket) ↔ CoordinatorRmi (Socket + RMI) ↔ NodeServerRmi (RMI)
ClientSimulator: Console app for user registration, login, and issuing FileCommand.

CoordinatorRmi: Listens on TCP port 6000, validates users (users.json), enforces department rules, selects a live node via RMI, and forwards commands.

NodeServerRmi: Each node hosts departmental files under node_storage/<department>, exposes RMI methods (getSyncList, sendFile, hasFile, writeFile, deleteFile), and runs a sync thread.

Prerequisites
Java 11+ (JDK)

Maven (or equivalent) for building

Localhost network connectivity

A users.json file in src/data/ containing user credentials

Project Structure
project-root/
├─ src/
│  ├─ main/
│  │  ├─ java/
│  │  │  ├─ org/example/client/ClientSimulator.java
│  │  │  ├─ org/example/coordinator/CoordinatorRmi.java
│  │  │  ├─ org/example/rmi/NodeService.java
│  │  │  ├─ org/example/rmi/NodeServerRmi.java
│  │  │  └─ org/example/model/
│  │  └─ resources/data/users.json
├─ node_storage/  (auto-created per department at runtime)
└─ pom.xml

Setup & Execution
Build the Project
mvn clean compile


Launch Node Servers (three terminals)
# Development node
java -cp target/classes org.example.rmi.NodeServerRmi 5001 development

# QA node
java -cp target/classes org.example.rmi.NodeServerRmi 5002 qa

# Design node
java -cp target/classes org.example.rmi.NodeServerRmi 5003 design

Each node will:

Create its directory node_storage/<department>

Bind its RMI service on the specified port

Start synchronizing with peers every 60 seconds

Start Coordinator
java -cp target/classes org.example.rmi.CoordinatorRmi

The Coordinator will:

Listen on TCP port 6000 for client requests

Spawn a retry thread that processes queued commands every 30 seconds

Run Client Simulator
java -cp target/classes org.example.client.ClientSimulator


Usage
Register (Managers only)

Enter manager token

Provide new username, department, role

Receive a generated token for the new user

Login

Enter user token

On success: Logged in as: <username> [<role>] [<department>]

File Commands

ADD <file>: Create a new file (enter content next)

UPDATE <file>: Overwrite an existing file (enter content next)

DELETE <file>: Remove a file

VIEW <file>: Retrieve and display a file’s content (from any department)

EXIT: Close the client

نسخ
تحرير
نسخ
تحر
