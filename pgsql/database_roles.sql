--
-- Copyright 2018 Jan Dittberner
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

CREATE DATABASE demoapp WITH ENCODING 'UTF8' TEMPLATE template0;
CREATE ROLE grp_demo_app_user
  LOGIN;

\c demoapp

-- create a user for vault
CREATE ROLE vaultadmin WITH NOCREATEDB
  CREATEROLE
  ADMIN grp_demo_app_user
  LOGIN
  PASSWORD 'superinsecure';

CREATE TABLE message (
  id      BIGSERIAL PRIMARY KEY,
  created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  message TEXT      NOT NULL
);

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO grp_demo_app_user;
GRANT USAGE, UPDATE ON ALL SEQUENCES IN SCHEMA public TO grp_demo_app_user;
