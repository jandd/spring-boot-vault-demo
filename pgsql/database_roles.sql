CREATE DATABASE demoapp;
CREATE ROLE grp_demo_app_user LOGIN;
GRANT ALL PRIVILEGES ON DATABASE demoapp TO grp_demo_app_user;

-- create a user for vault
CREATE ROLE vaultadmin WITH NOCREATEDB
  CREATEROLE
  ADMIN grp_demo_app_user
  LOGIN
  PASSWORD 'superinsecure';