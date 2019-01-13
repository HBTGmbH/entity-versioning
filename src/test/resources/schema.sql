-- We have to generate our identity sequence manually.
-- For everything else we use Hibernate's schema generation.
CREATE SEQUENCE IF NOT EXISTS identity_seq;
