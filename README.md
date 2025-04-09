# SimpleDB

SimpleDB is a lightweight relational database engine built from scratch in Java. It includes the core components needed to store, query, and manage structured data:

- Classes for representing fields, tuples, and schemas
- Predicate and condition evaluators for tuple filtering
- Disk-based access methods (e.g., heap files) for relation storage and iteration
- A set of operator classes for query processing (e.g., select, join, insert, delete)
- A buffer pool for caching data and managing transactions with concurrency control
- A catalog to keep track of table metadata and schemas
- Log-based rollback for aborts and log-based crash recovery

SimpleDB is designed as an educational tool to provide insight into the internal workings of a basic database system.
