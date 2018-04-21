# Spring Boot vault demo

## Getting started

Create an empty `bootstrap.yml`:

```
touch bootstrap.yml
```

Run gradle to build all necessary files:

```
./gradlew build
```

### Setup vault

Start the vault container:

```
docker-compose up --build vault
```

Initialize Vault:

```
docker-compose --no-ansi exec -e VAULT_CLI_NO_COLOR=1 -e VAULT_CACERT=/vault/config/ssl/vault.crt.pem vault \
  vault operator init -key-shares=1 -key-threshold=1 > vault_data.txt
```

Keep the information you will need the root token and unseal key later.

Unseal Vault:

```
docker-compose exec -e VAULT_CACERT=/vault/config/ssl/vault.crt.pem vault \
  vault operator unseal <unseal key from vault_data.txt>
```

Check the root token to see if Vault is working:

```
docker-compose exec -e VAULT_CACERT=/vault/config/ssl/vault.crt.pem vault \
  -e VAULT_TOKEN=<token from vault_data.txt> \
  vault token lookup
```

> You might want to put the docker-compose command line into a shell script, as it will be used a lot later. The
> following examples assume a `./vault_root.sh` with this content:
>
> ```
> #!/bin/sh
> set -e
> 
> docker-compose exec \
>   -e VAULT_CACERT=/vault/config/ssl/vault.crt.pem \  
>   -e VAULT_TOKEN=<token from vault_data.txt> \
>   vault vault "$@"
> ```

### Run the spring-boot application

```
docker-compose up --build web
```

> To run the spring-boot application from within an IDE you need to copy `build/resources/main/vault-truststore.jks`
> to `src/main/resources` so that it is on the classpath.

On the first attempt running the application will fail with:

> `java.lang.IllegalArgumentException: Token (spring.cloud.vault.token) must not be empty`

### Create a new token for your application

Use vault to create a new application token:

```
./vault_root.sh token create -policy=default
```

Put the following block into `bootstrap.yml`:

```yaml
spring.cloud.vault:
  token: <token-from-the-vault-output>
```

> To run the spring-boot application from an IDE you will need to add another line:
>
> ```yaml
>   host: localhost
> ```
>
> take care of the indentation (2 spaces)

Now try again:

```
./gradlew build && docker-compose up --build web
```

Now the application starts, but we get the following log messages (lines wrapped for readability):

> ```
> [RequestedSecret [path='secret/hello-vault', mode=ROTATE]]
>   Lease [leaseId='null', leaseDuration=PT0S, renewable=false]
>   Status 403 secret/hello-vault: permission denied
> [RequestedSecret [path='secret/application', mode=ROTATE]]
>   Lease [leaseId='null', leaseDuration=PT0S, renewable=false] 
>   Status 403 secret/application: permission denied
> ```

This is caused by Vaults default policy ACLs that do not allow access to the `secret` path.

Add a new policy to allow access for the application, use
[Vault's API](https://www.vaultproject.io/api/system/policy.html#create-update-policy) to do this (docker-compose
cannot access local files that are not shared with the vault container in advance):

```
curl -H "X-Vault-Token: <root token from vault_data.txt>" \
  --cacert vault/config/ssl/vault.crt.pem \
  --request PUT --data @./vault/hello-application.json \
  https://localhost:8200/v1/sys/policy/hello-application 
```

Create a new token for the application:

```
./vault_root.sh token create -policy=hello-application
```

Put the new token in `bootstrap.yml` and restart the application:

```
docker-compose stop web
./gradlew build
docker-compose up --build web
```

Now the application starts without errors, check to see the response:

```
http :8080

HTTP/1.1 200 
Content-Length: 12
Content-Type: text/plain;charset=UTF-8
Date: Mon, 26 Mar 2018 09:21:56 GMT

Hello World!
```

# Adding a secret to vault

The application looks for a value named *who* at `secret/hello-vault`, add it to Vault:

```
./vault_root.sh write secret/hello-vault who=You
```

Restart the application and see the effect:

```
http :8080

HTTP/1.1 200 
Content-Length: 10
Content-Type: text/plain;charset=UTF-8
Date: Mon, 26 Mar 2018 09:24:39 GMT

Hello You!
```

# Approle authentication

Vault Approle authentication credentials consists of an approle id and a secret id. Using these credentials the
application retrieves a temporary token from Vault. The application role determines which policies are applied for
the application.

## Setup application role

* enable approle authentication in vault
  ```
  ./vault_root.sh auth enable approle
  ```
* create the application role with the existing policy
  ```
  ./vault_root.sh write auth/approle/role/hello-vault policies=hello-application
  ```
* get the role-id from vault and put it into `bootstrap.yml`
  ```
  ./vault_root.sh read -field=role_id auth/approle/role/hello-vault/role-id
  echo "spring.cloud.vault.app-role.role-id: <role-id>" > bootstrap.yml
  ```
* create a secret id and export it as environment variable
  ```
  ./vault_root.sh write -f auth/approle/role/hello-vault/secret-id
  export SPRING_CLOUD_VAULT_APP_ROLE_SECRET_ID=<secret-id-from-vault>
  ```
* rebuild and run the web application container
  ```
  ./gradlew build && docker-compose up --build web
  ```

# Using the PKI secret engine

Vault provides an integrated PKI system. The application uses this secret engine to obtain a dynamically generated
server certificate.

## Setup vault PKI

* enable the pki secrets engine
  ```
  ./vault_root.sh secrets enable pki
  ```
* update the application policy to allow access to relevant pki paths
  ```
  curl -H "X-Vault-Token: <root token from vault_data.txt>" \
    --cacert vault/config/ssl/vault.crt.pem \
    --request PUT --data @./vault/hello-application.json \
    https://localhost:8200/v1/sys/policy/hello-application 
  ```
* generate a root CA certificate for the pki secrets engine
  ```
  ./vault_root.sh write pki/root/generate/internal "common_name=Demo Vault CA"
  ```
* setup a pki role for server certificates
  ```
  ./vault_root.sh write pki/roles/server client_flag=false ttl=24h
  ```
* get the vault pki CA certificate
  ```
  curl --cacert vault/config/ssl/vault.crt.pem https://localhost:8200/v1/pki/ca/pem > vaultca.pem
  ```

## Run the spring-boot application

* build and run
  ```
  ./gradlew build && docker-compose up --build web
  ```
* test the application
  ```
  http --default-scheme https --verify vaultca.pem :8443

  HTTP/1.1 200 
  Content-Length: 16
  Content-Type: text/plain;charset=UTF-8
  Date: Mon, 26 Mar 2018 14:04:53 GMT
  
  Hello DevDay 18!
  ```
* check that it is really TLS encrypted
  ```
  openssl s_client -connect localhost:8443 -CAfile vaultca.pem
  CONNECTED(00000003)
  depth=1 CN = Demo Vault CA
  verify return:1
  depth=0 CN = localhost
  verify return:1
  ---
  Certificate chain
   0 s:/CN=localhost
     i:/CN=Demo Vault CA
   1 s:/CN=Demo Vault CA
     i:/CN=Demo Vault CA
  ---
  ...
  SSL-Session:
      Protocol  : TLSv1.2
      Cipher    : ECDHE-RSA-AES256-GCM-SHA384
  ```
  
# Using the database secrets engine with PostgreSQL

* Start the db and restart the vault container:
  ```
  docker-compose up db
  docker-compose restart vault 
  ```
* Vault will start sealed, unseal it
  ```
  docker-compose exec -e VAULT_CLI_NO_COLOR=1 -e VAULT_CACERT=/vault/config/ssl/vault.crt.pem vault \
    vault operator unseal <unseal key from vault_data.txt>
  ```
* Setup database and roles
  ```
  psql -h localhost -U postgres -d postgres -f pgsql/database_roles.sql --port 15432
  ```
* Enable the database secrets engine
  ```
  ./vault_root.sh secrets enable database
  ```
* Setup the connection from Vault to PostgreSQL
  ```
  ./vault_root.sh write database/config/demodb plugin_name=postgresql-database-plugin allowed_roles=springdemo \
    connection_url="postgresql://{{username}}:{{password}}@db:5432/demoapp?sslmode=disable" \
    username="vaultadmin" \
    password="superinsecure"
  ```
  > You should enable SSL for your PostgreSQL server in production and set a better password for the vault user!
* Create a database role definition for the spring demo application
  ```
  ./vault_root.sh write database/roles/springdemo db_name=demodb \
    "creation_statements=CREATE ROLE \"{{name}}\" IN ROLE grp_demo_app_user LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}';" \
    default_ttl=24h max_ttl=72h
  ```
* Try to retrieve database credentials
  ```
  ./vault_root.sh read database/creds/springdemo
  ```
* Try to connect to PostgreSQL with these credentials
  ```
  psql -h localhost -U <username-from-vault-output> --port 15432 demoapp
  ```
* Update the ACL for the hello-application Vault policy
  ```
  curl -H "X-Vault-Token: <root token from vault_data.txt>" \
    --cacert vault/config/ssl/vault.crt.pem \
    --request PUT --data @./vault/hello-application.json \
    https://localhost:8200/v1/sys/policy/hello-application 
  ```
* Run the application
  ```
  ./gradlew build && docker compose up --build web
  ```
* Test the `/data/messages` REST endpoint to interact with the database
  ```
  http --verify vaultca.pem --default-scheme https :8443/data/messages message="Test message"
  http --verify vaultca.pem --default-scheme https :8443/data/messages
  ```