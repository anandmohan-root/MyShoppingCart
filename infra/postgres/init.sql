-- One database per service. They share one PostgreSQL server (and one test user) only to
-- keep the sample light; no service reads another service's database.
CREATE DATABASE catalog;
CREATE DATABASE orders;
CREATE DATABASE payments;
