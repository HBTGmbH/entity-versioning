[![Build Status](https://travis-ci.org/HBTGmbH/entity-versioning.svg?branch=master)](https://travis-ci.org/HBTGmbH/entity-versioning)

# What?

Transparent versioning of JPA entities (for Hibernate) with optimal query-time performance and full Spring Boot integration.

# Why?

Because currently there is no such solution available fitting these requirements:

* all versions of all entities for a given entity type reside in the same main table
* creating new versions of an entity transparently creates new versions of referencing entities
* only creating new versions of updated entities when necessary (including collections)

# How?

Add `de.hbt.entity.versioning:entity-versioning` as a Maven dependency to your project. With it comes version `5.4.0` of Hibernate Core as a necessary dependency.
Import the Spring `@Configuration` class `de.hbt.entity.versioning.VersioningSpringConfiguration` in your application, preferably as an `@Import(VersioningSpringConfiguration)` on your Spring Boot main class annotated with `@SpringBootApplication`.

# So, Hibernate Envers?

Not exactly. Envers solves a different use-case, that is, auditing and historization of entities. Envers does this by using separate auditing tables to store all old versions of an entity into, while the main tables always contain the latest version. This makes it difficult to perform efficient queries to navigate any given version of an entity, since Envers has to do cross-joins and aggregate operations on the global timestamp to find the right version of a referenced entity.

entity-versioning on the other hand keeps all versions of all entities in the main tables and ensures that navigating any version is performed in the most efficient way using the standard way of Hibernate/JPA (e.g. with simple joins), since all foreign key references are per version of an entity. As a consequence, when updating any entity, entity-versioning transitively creates new versions of all entities referencing any modified entity. And it does so completely transparently to the user application.
