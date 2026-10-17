# Eum-ShoppingMall-backend
이음산업건설 쇼핑몰 백엔드

## Database migrations

MySQL schema changes are managed by Flyway. A fresh database applies the versioned scripts in `src/main/resources/db/migration` before Hibernate validates the entity mappings. Do not edit a migration after it has been applied; add the next sequential `V<number>__description.sql` file in the same change as the entity update.

The `test` profile uses H2 schema generation and disables Flyway because the migration scripts target MySQL. CI starts an empty MySQL 8.4 database and starts the application with `ddl-auto: validate`, so a migration that fails or does not match the entities blocks the required `Validate database migrations` check.

The `local` profile also uses Flyway and Hibernate validation. It baselines a pre-existing local schema at version 1 to preserve databases previously created with Hibernate `update`; this baseline is local-only. Production does not enable automatic baselining. Before connecting Flyway to any existing production database, inspect its schema and baseline it deliberately. The initial migration does not create default categories or administrator accounts; application bootstrap remains responsible for those records.
